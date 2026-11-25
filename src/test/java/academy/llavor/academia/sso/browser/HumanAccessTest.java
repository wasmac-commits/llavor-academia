package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static academy.llavor.academia.sso.browser.BrowserFixture.*;
class HumanAccessTest {
    BrowserDestinationService destinations;SsoHttpClient client;MutableClock clock;HumanAccessService access;
    @BeforeEach void setup(){
        destinations=mock(BrowserDestinationService.class);client=mock(SsoHttpClient.class);clock=new MutableClock();
        access=new HumanAccessService(destinations,client,clock);access.configuration=mock(SsoConfiguration.class);
        when(access.configuration.portalLoginUrl()).thenReturn(java.net.URI.create("https://portal.example.test/llavor-portal/login.xhtml"));when(destinations.load(C)).thenReturn(destination(false));
        when(client.revalidate(any())).thenReturn(new SsoRevalidation(grant(),NOW));
    }
    Request logged()throws Exception{var r=new Request(false,"/index.xhtml");when(r.req.getMethod()).thenReturn("GET");r.human(new HumanSession(grant(),NOW,CSRF,settings(false).academyBaseUrl()));return r;}
    @Test void revalidatesOncePerRequestButNeverCachesAcrossRequests()throws Exception{
        var r=logged();access.check(r.req);access.check(r.req);verify(client,times(1)).revalidate(grant());
        r.attributes.clear();access.check(r.req);verify(client,times(2)).revalidate(grant());
        var view=(HumanView)r.attributes.get(BrowserHttp.VIEW);assertEquals("Synthetic user",view.displayName());assertEquals("ROOM_TEST",view.classroomCode());
    }
    @Test void revokedAccessFailsClosedAndInvalidates()throws Exception{
        for(var code:new SsoClientFailure.Code[]{SsoClientFailure.Code.ACCESS_DENIED}){
            var r=logged();var session=r.current.get();var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
            doThrow(new SsoClientFailure(code)).when(client).revalidate(any());
            new HumanAccessFilter(access).doFilter(r.req,response,chain);
            verify(session).invalidate();verify(chain,never()).doFilter(any(),any());verify(response).setStatus(303);
            verify(response).setHeader("Location","https://portal.example.test/llavor-portal/login.xhtml");assertNull(r.attributes.get(BrowserHttp.VIEW));
        }
    }
    @Test void absenceIdleAndAbsoluteExpiryDoNotContactPortal()throws Exception{
        var missing=new Request(false,"/index.xhtml");assertThrows(BrowserFailure.class,()->access.check(missing.req));
        var r=logged();clock.value=NOW.plusSeconds(1800);var idle=r;
        assertThrows(BrowserFailure.class,()->access.check(idle.req));
        clock.value=NOW;
        var longGrant=new SsoAuthorization(ACCESS,P,1,A,C,NOW,NOW.plusSeconds(9*3600),"Synthetic");
        var human=new HumanSession(longGrant,NOW,CSRF,settings(false).academyBaseUrl());assertEquals(NOW.plusSeconds(8*3600),human.expiresAt());
        human.touch(NOW.plusSeconds(7*3600));assertFalse(human.valid(NOW.plusSeconds(8*3600)));
        verifyNoInteractions(client);
    }
    @Test void transientPortalFailurePreservesSessionButDoesNotAuthorizeOfflineAccess()throws Exception{
        var r=logged();var session=r.current.get();var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
        doThrow(new SsoClientFailure(SsoClientFailure.Code.REMOTE_UNAVAILABLE)).when(client).revalidate(any());new HumanAccessFilter(access).doFilter(r.req,response,chain);
        verify(session,never()).invalidate();verify(response).setStatus(503);verifyNoInteractions(chain);assertSame(session,r.current.get());
        assertTrue(session.getAttribute(HumanSession.ATTRIBUTE) instanceof HumanSession);
    }
    @Test void manipulatedQueryUnknownRoutesAndMethodDoNotReachApplication()throws Exception{
        var filter=new HumanAccessFilter(access);var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
        var r=logged();when(r.req.getQueryString()).thenReturn("classroomId="+P);
        filter.doFilter(r.req,response,chain);verifyNoInteractions(client);verify(chain,never()).doFilter(any(),any());
        for(String path:new String[]{"/sso/diagnostic","/internal/other","/index.xhtml;jsessionid=x","/other.xhtml"}){
            var request=new Request(false,path);filter.doFilter(request.req,response,chain);
        }
        verifyNoInteractions(client);
    }
    @Test void exactK01RoutesAndPackagedResourcesRemainIndependent()throws Exception{
        var filter=new HumanAccessFilter(access);var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
        for(String path:new String[]{"/internal/v1/academies/"+A+"/identity",
            "/internal/v1/academies/"+A+"/classrooms/"+C,
            "/internal/v1/academies/"+A+"/classrooms/"+C+"/provisioning-operations/"+ACCESS}){
            filter.doFilter(new Request(false,path).req,response,chain);assertTrue(HumanAccessFilter.technical(path));
        }
        var resource=new Request(false,"/jakarta.faces.resource/css/academia.css.xhtml");when(resource.req.getMethod()).thenReturn("GET");when(resource.req.getQueryString()).thenReturn("ln=llavor");
        filter.doFilter(resource.req,response,chain);verify(chain,times(4)).doFilter(any(),any());verifyNoInteractions(client,destinations);
        assertFalse(HumanAccessFilter.technical("/internal/not-authorized"));assertFalse(HumanAccessFilter.resource("/resources/private.xhtml"));assertFalse(HumanAccessFilter.resource("/jakarta.faces.resource/dynamiccontent.properties.xhtml"));
    }
    @Test void onlyAuthenticatedHumanPageUsesOriginOnlyForLogout()throws Exception{
        var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
        var filter=new HumanAccessFilter(access);var r=logged();filter.doFilter(r.req,response,chain);
        var order=inOrder(response);order.verify(response).setHeader("Referrer-Policy","no-referrer");order.verify(response).setHeader("Referrer-Policy","strict-origin");
        verify(chain).doFilter(r.req,response);
        var publicResponse=mock(HttpServletResponse.class);var publicPage=new Request(false,"/access-required.xhtml");when(publicPage.req.getMethod()).thenReturn("GET");
        filter.doFilter(publicPage.req,publicResponse,mock(FilterChain.class));verify(publicResponse,never()).setHeader("Referrer-Policy","strict-origin");
        var denied=mock(HttpServletResponse.class);doThrow(new BrowserFailure("SESSION_REQUIRED")).when(access=mock(HumanAccessService.class)).check(any());
        new HumanAccessFilter(access).doFilter(logged().req,denied,mock(FilterChain.class));verify(denied,never()).setHeader("Referrer-Policy","strict-origin");
    }
    @Test void headerResourcesAreExactlyAllowlistedWithoutOpeningOtherFacesResources()throws Exception{
        var filter=new HumanAccessFilter(access);
        for(String path:new String[]{"/jakarta.faces.resource/css/llavor-theme.css.xhtml","/jakarta.faces.resource/images/llavor-academy-horizontal.png.xhtml","/jakarta.faces.resource/primeicons/primeicons.css.xhtml","/jakarta.faces.resource/primeicons/primeicons.woff2.xhtml"}){
            var r=new Request(false,path);when(r.req.getMethod()).thenReturn("GET");when(r.req.getQueryString()).thenReturn(path.contains("primeicons")?"ln=primefaces&v=15.0.15":"ln=llavor");
            var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);filter.doFilter(r.req,response,chain);verify(chain).doFilter(r.req,response);
            when(r.req.getQueryString()).thenReturn("ln=other");var denied=mock(FilterChain.class);filter.doFilter(r.req,mock(HttpServletResponse.class),denied);verifyNoInteractions(denied);
            when(r.req.getQueryString()).thenReturn("ln=llavor&ln=primefaces");filter.doFilter(r.req,mock(HttpServletResponse.class),denied);verifyNoInteractions(denied);
            when(r.req.getMethod()).thenReturn("POST");filter.doFilter(r.req,mock(HttpServletResponse.class),denied);verifyNoInteractions(denied);
        }
        for(String path:new String[]{"/jakarta.faces.resource/primefaces.js.xhtml","/resources/llavor/images/other.png"})assertFalse(HumanAccessFilter.resource(path));
        verifyNoInteractions(client,destinations);
    }
    @Test void directPrivateUrlWithoutSessionGoesToConfiguredPortalLogin()throws Exception{
        var r=new Request(false,"/index.xhtml");when(r.req.getMethod()).thenReturn("GET");
        var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
        new HumanAccessFilter(access).doFilter(r.req,response,chain);
        verify(response).setHeader("Location","https://portal.example.test/llavor-portal/login.xhtml");verifyNoInteractions(chain,client);
        verify(r.req,never()).getSession(true);
    }
    @Test void forwardedPrivateTargetCannotInheritPublicResourceException()throws Exception{
        var r=new Request(false,"/index.xhtml");when(r.req.getMethod()).thenReturn("GET");
        when(r.req.getDispatcherType()).thenReturn(DispatcherType.FORWARD);
        r.attributes.put(RequestDispatcher.FORWARD_REQUEST_URI,"/llavor-academia/resources/llavor/css/academia.css");
        var chain=mock(FilterChain.class);new HumanAccessFilter(access).doFilter(r.req,mock(HttpServletResponse.class),chain);
        verifyNoInteractions(chain,client);
    }
    @Test void ssoGetAndQueryVariantsCannotReachServlet()throws Exception{
        for(String path:new String[]{"/sso/start","/sso/enter","/sso/logout"}){
            var r=new Request(false,path);when(r.req.getMethod()).thenReturn("GET");
            var response=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);
            new HumanAccessFilter(access).doFilter(r.req,response,chain);verify(response).setStatus(405);verifyNoInteractions(chain);
            when(r.req.getMethod()).thenReturn("POST");when(r.req.getQueryString()).thenReturn("classroomId="+C);
            var bad=mock(HttpServletResponse.class);new HumanAccessFilter(access).doFilter(r.req,bad,chain);verify(bad).setStatus(400);verifyNoInteractions(chain);
        }
    }
    @Test void contradictoryRevalidationCannotChangeClassroom()throws Exception{
        var r=logged();when(client.revalidate(any())).thenReturn(new SsoRevalidation(new SsoAuthorization(ACCESS,P,1,A,P,NOW,NOW.plusSeconds(3600),"Other"),NOW));
        assertThrows(BrowserFailure.class,()->access.check(r.req));assertNull(r.attributes.get(BrowserHttp.VIEW));
    }
    @Test void languagePostStillRequiresAnAuthorizedHumanAndClosedQuery()throws Exception{
        var filter=new HumanAccessFilter(access);var r=logged();when(r.req.getMethod()).thenReturn("POST");
        var chain=mock(FilterChain.class);var response=mock(HttpServletResponse.class);filter.doFilter(r.req,response,chain);verify(chain).doFilter(r.req,response);verify(client).revalidate(grant());
        var missing=new Request(false,"/index.xhtml");var denied=mock(FilterChain.class);filter.doFilter(missing.req,mock(HttpServletResponse.class),denied);verifyNoInteractions(denied);
        when(r.req.getQueryString()).thenReturn("language=ca-ES-valencia");filter.doFilter(r.req,mock(HttpServletResponse.class),denied);verifyNoInteractions(denied);
    }
    @Test void selectorStaticResourcesAreAllowedOnlyWithTheirOwnLibrary()throws Exception{
        var filter=new HumanAccessFilter(access);
        for(String path:new String[]{"/jakarta.faces.resource/core.js.xhtml","/jakarta.faces.resource/components.js.xhtml","/jakarta.faces.resource/components.css.xhtml","/jakarta.faces.resource/jquery/jquery.js.xhtml","/jakarta.faces.resource/jquery/jquery-plugins.js.xhtml","/jakarta.faces.resource/theme.css.xhtml"}){
            var r=new Request(false,path);when(r.req.getMethod()).thenReturn("GET");when(r.req.getQueryString()).thenReturn("ln="+(path.contains("theme.css")?"primefaces-saga-blue":"primefaces")+"&v=15.0.15");var chain=mock(FilterChain.class);var response=mock(HttpServletResponse.class);filter.doFilter(r.req,response,chain);verify(chain).doFilter(r.req,response);
            when(r.req.getQueryString()).thenReturn("ln=other");var denied=mock(FilterChain.class);filter.doFilter(r.req,mock(HttpServletResponse.class),denied);verifyNoInteractions(denied);
        }
        verifyNoInteractions(client,destinations);
    }
}
