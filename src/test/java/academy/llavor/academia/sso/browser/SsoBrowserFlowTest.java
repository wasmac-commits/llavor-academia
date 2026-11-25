package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static academy.llavor.academia.sso.browser.BrowserFixture.*;
class SsoBrowserFlowTest {
    BrowserDestinationService destinations;SsoHttpClient client;BrowserTokens tokens;MutableClock clock;
    PendingBrowserContexts contexts;SsoBrowserService service;
    @BeforeEach void setup(){
        destinations=mock(BrowserDestinationService.class);client=mock(SsoHttpClient.class);tokens=mock(BrowserTokens.class);clock=new MutableClock();
        contexts=new PendingBrowserContexts(clock);service=new SsoBrowserService(destinations,contexts,tokens,client,clock);
        when(destinations.load(C)).thenReturn(destination(false));when(tokens.next()).thenReturn(BINDING,CSRF);
        when(client.consume(eq(TICKET),anyString(),eq(C))).thenReturn(grant());
    }
    @Test void startBindsBrowserAndConsumesOnceThenRotatesSession()throws Exception{
        var start=new Request(false,"/sso/start");
        var opened=service.start(start.req,BrowserFixture.start());
        verify(start.req,never()).getSession(anyBoolean());verifyNoInteractions(client);
        assertEquals(BrowserTokens.hash(BINDING),opened.context().bindingHash());assertFalse(opened.context().toString().contains(BINDING));
        var enter=new Request(false,"/sso/enter");HttpSession old=enter.session();enter.current.set(old);
        service.enter(enter.req,BrowserFixture.enter(opened.context().id()));
        verify(old).invalidate();verify(enter.req).changeSessionId();
        var newSession=enter.current.get();assertNotSame(old,newSession);verify(newSession).setMaxInactiveInterval(1800);
        var human=(HumanSession)newSession.getAttribute(HumanSession.ATTRIBUTE);
        assertEquals(grant(),human.authorization());assertEquals(1,enter.sessions.get(newSession).size());
        assertThrows(BrowserFailure.class,()->service.enter(enter.req,BrowserFixture.enter(opened.context().id())));
        verify(client,times(1)).consume(TICKET,BrowserTokens.hash(BINDING),C);
        var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(human);}
        String serialized=bytes.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(serialized.contains(TICKET));assertFalse(serialized.contains(BINDING));assertFalse(serialized.contains("superadmin"));
        assertFalse(human.toString().contains(CSRF));
    }
    @Test void wrongOriginOrCookieCannotConsume()throws Exception{
        var opened=service.start(new Request(false,"/sso/start").req,BrowserFixture.start());
        var bad=new Request(false,"/sso/enter");bad.origin("https://evil.test");
        assertThrows(BrowserFailure.class,()->service.enter(bad.req,BrowserFixture.enter(opened.context().id())));
        bad.origin("http://localhost:8080");when(bad.req.getCookies()).thenReturn(new Cookie[]{new Cookie(BrowserHttp.BINDING,"E".repeat(42)+"A")});
        assertThrows(BrowserFailure.class,()->service.enter(bad.req,BrowserFixture.enter(opened.context().id())));
        verifyNoInteractions(client);
    }
    @Test void duplicateBindingCookiesAndInvalidTicketNeverReachPortal()throws Exception{
        var opened=service.start(new Request(false,"/sso/start").req,BrowserFixture.start());var request=new Request(false,"/sso/enter");
        when(request.req.getCookies()).thenReturn(new Cookie[]{new Cookie(BrowserHttp.BINDING,BINDING),new Cookie(BrowserHttp.BINDING,FLOW)});
        assertThrows(BrowserFailure.class,()->service.enter(request.req,BrowserFixture.enter(opened.context().id())));
        var fields=new HashMap<>(BrowserFixture.enter(opened.context().id()));fields.put("ticket","invalid");
        assertThrows(BrowserFailure.class,()->service.enter(request.req,fields));verifyNoInteractions(client);
    }
    @Test void expiredAndConcurrentContextsHaveExactlyOneConsumer()throws Exception{
        var opened=service.start(new Request(false,"/sso/start").req,BrowserFixture.start());
        clock.value=NOW.plusSeconds(120);
        assertThrows(BrowserFailure.class,()->service.enter(new Request(false,"/sso/enter").req,BrowserFixture.enter(opened.context().id())));
        verifyNoInteractions(client);clock.value=NOW;
        var ctx=contexts.issue(settings(false),BrowserTokens.hash(BINDING));
        try(var pool=Executors.newFixedThreadPool(2)){
            var latch=new CountDownLatch(1);
            Callable<Boolean> call=()->{var request=new Request(false,"/sso/enter");latch.await();try{service.enter(request.req,BrowserFixture.enter(ctx.id()));return true;}catch(BrowserFailure e){return false;}};
            var first=pool.submit(call);var second=pool.submit(call);latch.countDown();
            assertNotEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));
        }
        verify(client,times(1)).consume(anyString(),anyString(),eq(C));
    }
    @Test void uncertainConsumeIsNotReplayedAndNoSessionEstablished()throws Exception{
        var opened=service.start(new Request(false,"/sso/start").req,BrowserFixture.start());var request=new Request(false,"/sso/enter");
        when(client.consume(anyString(),anyString(),any())).thenThrow(new SsoClientFailure(SsoClientFailure.Code.TIMEOUT,true));
        assertThrows(SsoClientFailure.class,()->service.enter(request.req,BrowserFixture.enter(opened.context().id())));
        assertThrows(BrowserFailure.class,()->service.enter(request.req,BrowserFixture.enter(opened.context().id())));
        verify(client,times(1)).consume(anyString(),anyString(),any());verify(request.req,never()).getSession(true);
    }
    @Test void invalidOrContradictoryGrantNeverCreatesSession()throws Exception{
        var opened=service.start(new Request(false,"/sso/start").req,BrowserFixture.start());var request=new Request(false,"/sso/enter");
        var wrong=new SsoAuthorization(ACCESS,P,1,A,P,NOW,NOW.plusSeconds(60),"Other");
        when(client.consume(anyString(),anyString(),any())).thenReturn(wrong);
        assertThrows(BrowserFailure.class,()->service.enter(request.req,BrowserFixture.enter(opened.context().id())));
        verify(request.req,never()).getSession(true);
    }
    @Test void localDisableAfterHttpDiscardsConsumedGrant()throws Exception{
        var opened=service.start(new Request(false,"/sso/start").req,BrowserFixture.start());var request=new Request(false,"/sso/enter");
        when(destinations.load(C)).thenReturn(destination(false)).thenThrow(new SsoClientFailure(SsoClientFailure.Code.DISABLED));
        assertThrows(SsoClientFailure.class,()->service.enter(request.req,BrowserFixture.enter(opened.context().id())));
        verify(request.req,never()).getSession(true);
    }
    @Test void noAnonymousRemoteOrWrongIdentityStart()throws Exception{
        var request=new Request(false,"/sso/start");request.origin("https://evil.test");
        assertThrows(BrowserFailure.class,()->service.start(request.req,BrowserFixture.start()));
        request.origin("http://localhost:8080");when(request.req.getRemoteAddr()).thenReturn("192.0.2.1");
        assertThrows(BrowserFailure.class,()->service.start(request.req,BrowserFixture.start()));
        var fields=new HashMap<>(BrowserFixture.start());fields.put("academyId",P.toString());
        assertThrows(BrowserFailure.class,()->service.start(new Request(false,"/sso/start").req,fields));
        verifyNoInteractions(client);
    }
    @Test void contextCapacityIsBoundedAndDoesNotExtendExpiry(){
        for(int i=0;i<PendingBrowserContexts.LIMIT;i++)contexts.issue(settings(false),Integer.toString(i));
        assertThrows(BrowserFailure.class,()->contexts.issue(settings(false),"next"));
        clock.value=NOW.plusSeconds(121);assertDoesNotThrow(()->contexts.issue(settings(false),"next"));
    }
    @Test void pendingContextRejectsChangedConfiguredDestination(){
        var ctx=contexts.issue(settings(false),BrowserTokens.hash(BINDING));
        assertThrows(BrowserFailure.class,()->contexts.claim(ctx.id(),BrowserTokens.hash(BINDING),settings(true)));
    }
}
