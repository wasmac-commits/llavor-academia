package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.beans.Introspector;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static academy.llavor.academia.sso.browser.BrowserFixture.*;
class BrowserBoundaryTest {
    SsoBrowserService service;SsoEntryServlet servlet;HttpServletResponse response;StringWriter output;
    @BeforeEach void setup()throws Exception{
        service=mock(SsoBrowserService.class);servlet=new SsoEntryServlet(service);response=mock(HttpServletResponse.class);output=new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void startUsesProtectedCookieAndPostOnlyContinuationWithoutSecretUrls(boolean secure)throws Exception{
        var r=new Request(secure,"/sso/start");
        r.body("contractVersion=1&portalFlowId="+FLOW+"&academyId="+A+"&classroomId="+C);
        var context=new PendingBrowserContexts.Context(ACCESS,settings(secure),BrowserTokens.hash(BINDING),NOW,NOW.plusSeconds(120));
        when(service.start(eq(r.req),anyMap())).thenReturn(new SsoBrowserService.Started(context,BINDING,FLOW));
        servlet.service(r.req,response);
        var cookie=org.mockito.ArgumentCaptor.forClass(Cookie.class);verify(response).addCookie(cookie.capture());
        assertTrue(cookie.getValue().isHttpOnly());assertEquals(secure,cookie.getValue().getSecure());
        assertEquals(secure?"None":"Lax",cookie.getValue().getAttribute("SameSite"));assertNull(cookie.getValue().getDomain());
        assertEquals("/llavor-academia/sso",cookie.getValue().getPath());assertEquals(120,cookie.getValue().getMaxAge());
        String html=output.toString();assertTrue(html.contains("method=\"post\""));assertTrue(html.contains("action=\""+settings(secure).technical().portalBaseUrl()+"/sso/continue\""));
        assertFalse(html.contains(BINDING));assertFalse(html.contains(TICKET));verify(response,never()).setHeader(eq("Location"),anyString());
        verify(response).setHeader("Cache-Control","no-store");
        var headers=inOrder(response);headers.verify(response).setHeader("Referrer-Policy","no-referrer");headers.verify(response).setHeader("Referrer-Policy","strict-origin");
        assertTrue(html.contains("id=\"sso-transfer\" method=\"post\" accept-charset=\"UTF-8\""));assertTrue(html.contains("<button type=\"submit\">Continuar en Portal</button>"));
        var script=java.util.regex.Pattern.compile("<script>([\\s\\S]*?)</script>").matcher(html);assertTrue(script.find());assertEquals(BrowserHttp.AUTO_POST_SCRIPT,script.group(1));assertFalse(script.find());
        String hash="'sha256-"+Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(BrowserHttp.AUTO_POST_SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+"'";assertEquals(BrowserHttp.AUTO_POST_CSP,hash);
        var styles=java.util.regex.Pattern.compile("<style>([\\s\\S]*?)</style>").matcher(html);assertTrue(styles.find());assertEquals(BrowserHttp.TRANSFER_STYLE,styles.group(1));assertFalse(styles.find());
        String styleHash="'sha256-"+Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(BrowserHttp.TRANSFER_STYLE.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+"'";assertEquals(BrowserHttp.TRANSFER_STYLE_CSP,styleHash);
        assertTrue(html.contains("<details><summary>Opciones de acceso</summary>"));assertFalse(html.contains("<details open"));assertTrue(html.indexOf("<details>")<html.indexOf("<form "));assertTrue(html.indexOf("</form>")<html.indexOf("</details>"));assertTrue(html.contains("<noscript>"));
        var csp=org.mockito.ArgumentCaptor.forClass(String.class);verify(response).setHeader(eq("Content-Security-Policy"),csp.capture());assertTrue(csp.getValue().contains("style-src "+styleHash+";"));assertTrue(csp.getValue().contains("script-src "+hash+";"));assertFalse(csp.getValue().contains("unsafe-inline"));assertFalse(csp.getValue().contains("unsafe-eval"));
        verify(response).setContentType("text/html; charset=UTF-8");assertTrue(html.contains("Accediendo a tu aula…"));assertTrue(html.contains("Opciones de acceso"));assertFalse(html.contains("Continúa"));
        Set<String> names=new HashSet<>();var fields=java.util.regex.Pattern.compile("<input[^>]+name=\"([^\"]+)\"").matcher(html);while(fields.find())assertTrue(names.add(fields.group(1)));assertEquals(Set.of("contractVersion","portalFlowId","contextId","portalInstanceId","academyId","classroomId","browserBindingHash"),names);

    }
    @Test void presentationNameIsEscapedAndRemovedBeforeAuthentication()throws Exception{
        var r=new Request(true,"/sso/start");
        r.body("contractVersion=1&portalFlowId="+FLOW+"&academyId="+A+"&classroomId="+C+"&uiDisplayName=Mar%C3%ADa+%3Cscript%3E");
        var context=new PendingBrowserContexts.Context(ACCESS,settings(true),BrowserTokens.hash(BINDING),NOW,NOW.plusSeconds(120));
        when(service.start(eq(r.req),anyMap())).thenAnswer(call->{
            Map<String,String> form=call.getArgument(1);assertFalse(form.containsKey("uiDisplayName"));
            return new SsoBrowserService.Started(context,BINDING,FLOW);
        });
        servlet.service(r.req,response);String html=output.toString();
        assertTrue(html.contains("class=\"llavor-topbar\""));
        assertTrue(html.contains("/llavor-academia/jakarta.faces.resource/images/llavor-header-white.jpg.xhtml?ln=llavor"));
        assertTrue(html.contains("Mar\u00eda &lt;script&gt;"));assertFalse(html.contains("Mar\u00eda <script>"));
        assertTrue(html.contains("name=\"uiDisplayName\" value=\"Mar\u00eda &lt;script&gt;\""));
    }
    @Test void failedStartKeepsNoReferrerAndNeverBuildsTransferForm()throws Exception{
        var r=new Request(true,"/sso/start");r.body("contractVersion=1&portalFlowId="+FLOW+"&academyId="+A+"&classroomId="+C);
        when(service.start(eq(r.req),anyMap())).thenThrow(new BrowserFailure("ORIGIN_MISMATCH"));
        servlet.service(r.req,response);verify(response).setStatus(403);verify(response,never()).setHeader("Referrer-Policy","strict-origin");
        assertFalse(output.toString().contains("<form"));assertFalse(output.toString().contains("<script"));assertFalse(output.toString().contains(FLOW));
    }
    @Test void nullOriginRemainsRejected()throws Exception{var r=new Request(true,"/sso/start");r.origin("null");assertThrows(BrowserFailure.class,()->BrowserHttp.origin(r.req,settings(true).portalOrigin()));}
    @Test void enterRedirectContainsNoTicketAndExpiresConsumedBinding()throws Exception{
        var r=new Request(false,"/sso/enter");r.body("contractVersion=1&contextId="+ACCESS+"&ticket="+TICKET);
        doAnswer(i->{r.attributes.put(SsoBrowserService.CLAIMED,true);return null;}).when(service).enter(eq(r.req),anyMap());
        servlet.service(r.req,response);verify(service,times(1)).enter(eq(r.req),anyMap());
        verify(response).setStatus(303);verify(response).setHeader("Location","/llavor-academia/index.xhtml");
        var cookie=org.mockito.ArgumentCaptor.forClass(Cookie.class);verify(response).addCookie(cookie.capture());assertEquals(0,cookie.getValue().getMaxAge());
    }
    @Test void failedEnterDoesNotLeakTicketOrBindingAndCannotRepeatAutomatically()throws Exception{
        var r=new Request(false,"/sso/enter");r.body("contractVersion=1&contextId="+ACCESS+"&ticket="+TICKET);
        doAnswer(i->{r.attributes.put(SsoBrowserService.CLAIMED,true);throw new SsoClientFailure(SsoClientFailure.Code.TIMEOUT,true);}).when(service).enter(eq(r.req),anyMap());
        servlet.service(r.req,response);verify(response).setStatus(403);verify(service,times(1)).enter(eq(r.req),anyMap());
        assertFalse(output.toString().contains(TICKET));assertFalse(output.toString().contains(BINDING));
        assertFalse(output.toString().contains("java."));assertTrue(output.toString().contains("Referencia:"));
    }
    @Test void logoutRevokesCentralSessionWithOriginAndCsrf()throws Exception{
        var r=new Request(true,"/sso/logout");r.origin(settings(true).academyOrigin());
        r.human(new HumanSession(grant(),NOW,CSRF,settings(true).academyBaseUrl()));
        var session=r.current.get();r.body("csrf="+CSRF);
        when(service.logout(any())).thenReturn(java.net.URI.create("https://portal.example.test/llavor-portal/login.xhtml"));
        servlet.service(r.req,response);verify(session).invalidate();
        verify(response).setStatus(303);verify(response).setHeader("Location","https://portal.example.test/llavor-portal/login.xhtml");verify(service).logout(any());
    }
    @Test void failedCentralLogoutClosesLocalSessionWithoutReportingSuccess()throws Exception{
        var r=new Request(true,"/sso/logout");r.origin(settings(true).academyOrigin());
        r.human(new HumanSession(grant(),NOW,CSRF,settings(true).academyBaseUrl()));var session=r.current.get();r.body("csrf="+CSRF);
        when(service.logout(any())).thenThrow(new SsoClientFailure(SsoClientFailure.Code.REMOTE_UNAVAILABLE));
        servlet.service(r.req,response);verify(session).invalidate();verify(response).setStatus(503);
        verify(response,never()).setHeader(eq("Location"),anyString());
    }
    @Test void logoutRejectsNullOriginWithoutInvalidatingSession()throws Exception{
        var r=new Request(true,"/sso/logout");r.origin("null");r.human(new HumanSession(grant(),NOW,CSRF,settings(true).academyBaseUrl()));var session=r.current.get();r.body("csrf="+CSRF);
        servlet.service(r.req,response);verify(response).setStatus(403);verify(session,never()).invalidate();verifyNoInteractions(service);
    }
    @Test void forgedLogoutCannotInvalidateSession()throws Exception{
        var r=new Request(true,"/sso/logout");r.origin(settings(true).academyOrigin());
        r.human(new HumanSession(grant(),NOW,CSRF,settings(true).academyBaseUrl()));var session=r.current.get();r.body("csrf="+BINDING);
        servlet.service(r.req,response);verify(response).setStatus(403);verify(session,never()).invalidate();
        assertFalse(output.toString().contains(CSRF));verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"contractVersion=1&contextId=x&ticket=x&extra=x","contractVersion=1&contextId=x&ticket=x&ticket=y","contractVersion=2","bad","%xx=x"})
    void malformedOrDuplicateFieldsNeverInvokeService(String body)throws Exception{
        var r=new Request(false,"/sso/enter");r.body(body);servlet.service(r.req,response);verifyNoInteractions(service);verify(response).setStatus(403);
    }
    @Test void postOnlyNoQueryAndBoundedUtf8Form()throws Exception{
        var r=new Request(false,"/sso/start");when(r.req.getMethod()).thenReturn("GET");servlet.service(r.req,response);
        verify(response).setStatus(405);verify(response).setHeader("Allow","POST");verifyNoInteractions(service);
        when(r.req.getMethod()).thenReturn("POST");r.body("a".repeat(2049));servlet.service(r.req,response);verifyNoInteractions(service);
        r.body("contractVersion=1&contextId="+ACCESS+"&ticket="+TICKET);when(r.req.getQueryString()).thenReturn("ticket=hidden");
        servlet.service(r.req,response);verifyNoInteractions(service);
    }
    @Test void absentDuplicatedOrUnknownOriginsAreRejected()throws Exception{
        var r=new Request(false,"/sso/start");
        when(r.req.getHeaders("Origin")).thenAnswer(i->Collections.emptyEnumeration());
        assertThrows(BrowserFailure.class,()->BrowserHttp.origin(r.req,"http://localhost:8080"));
        when(r.req.getHeaders("Origin")).thenAnswer(i->Collections.enumeration(List.of("http://localhost:8080","https://evil.test")));
        assertThrows(BrowserFailure.class,()->BrowserHttp.origin(r.req,"http://localhost:8080"));
    }
    @Test void cookiesAndWebXmlAndJavaBeansContractAreCompatible()throws Exception{
        var context=mock(ServletContext.class);var cookie=mock(SessionCookieConfig.class);when(context.getSessionCookieConfig()).thenReturn(cookie);
        new HumanSessionCookieConfig().contextInitialized(new ServletContextEvent(context));
        verify(cookie).setName("LLAVOR_ACADEMIA_SESSION");verify(cookie).setHttpOnly(true);verify(cookie).setAttribute("SameSite","Lax");
        var names=new HashSet<String>();for(var p:Introspector.getBeanInfo(AcademyHomeBean.class).getPropertyDescriptors())if(p.getReadMethod()!=null)names.add(p.getName());
        assertTrue(names.containsAll(Set.of("displayName","classroomName","classroomCode","academyCode","csrf")));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        for(String file:List.of("index.xhtml","access-required.xhtml","WEB-INF/web.xml"))factory.newDocumentBuilder().parse(Path.of("src/main/webapp",file).toFile());
        String xml=Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"));
        assertTrue(xml.contains("<tracking-mode>COOKIE</tracking-mode>"));assertTrue(xml.contains("/sso/start"));assertFalse(xml.contains("/sso/*"));
    }
    @Test void humanPagesRetainAccentsInUtf8AndValidXhtml()throws Exception{
        String home=academy.llavor.academia.testing.FaceletView.text("index.xhtml");
        String access=Files.readString(Path.of("src/main/webapp/access-required.xhtml"),java.nio.charset.StandardCharsets.UTF_8);
        var base=academy.llavor.academia.i18n.seed.AcademyInitialTexts.spanish();
        for(String key:List.of("academy.home.page_title","academy.home.subtitle","academy.home.technical","academy.home.coming","academy.session.logout_title"))assertTrue(home.contains("academyLanguageBean.text['"+key+"']"),key);
        assertTrue(access.contains("academy.access.expired"));assertTrue(base.get("academy.home.page_title").contains("Llavor Academia"));
        for(String text:base.values()){assertFalse(text.contains("?"));assertFalse(text.contains("\uFFFD"));}
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        for(String file:List.of("index.xhtml","access-required.xhtml"))factory.newDocumentBuilder().parse(Path.of("src/main/webapp",file).toFile());
    }
    @Test void traceContainsOnlySafeCodeActionAndCorrelation(){
        var logger=Logger.getLogger("academy.llavor.academia.sso.browser");var records=new ArrayList<LogRecord>();
        Handler handler=new Handler(){public void publish(LogRecord record){records.add(record);}public void flush(){}public void close(){}};
        logger.addHandler(handler);
        try{BrowserHttp.trace("ENTER",new IllegalStateException("SECRET "+TICKET+" "+BINDING));}
        finally{logger.removeHandler(handler);}
        assertEquals(1,records.size());var r=records.getFirst();assertNull(r.getThrown());
        String formatted=java.text.MessageFormat.format(r.getMessage(),r.getParameters());
        assertTrue(formatted.contains("code=UNAVAILABLE"));assertTrue(formatted.matches(".*ref=[0-9a-f-]{36}"));
        assertFalse(formatted.contains(TICKET));assertFalse(formatted.contains("SECRET"));
    }
    @Test void publicHttpsTransferNeverUsesTheTechnicalLoopbackAddress()throws Exception{
        var r=new Request(true,"/sso/start");r.body("contractVersion=1&portalFlowId="+FLOW+"&academyId="+A+"&classroomId="+C);
        var publicSettings=new BrowserSettings(settings(false).technical(),java.net.URI.create("https://public.example.test/llavor-academia"),java.net.URI.create("https://public.example.test/llavor-portal"));
        var context=new PendingBrowserContexts.Context(ACCESS,publicSettings,BrowserTokens.hash(BINDING),NOW,NOW.plusSeconds(120));
        when(service.start(eq(r.req),anyMap())).thenReturn(new SsoBrowserService.Started(context,BINDING,FLOW));
        servlet.service(r.req,response);String page=output.toString();
        assertTrue(page.contains("action=\"https://public.example.test/llavor-portal/sso/continue\""));
        assertFalse(page.contains("http://localhost"));
        var cookie=org.mockito.ArgumentCaptor.forClass(Cookie.class);verify(response).addCookie(cookie.capture());assertTrue(cookie.getValue().getSecure());assertTrue(cookie.getValue().isHttpOnly());
    }

    @Test void transferIdentityUsesExistingLocalAssetsWithoutVisibleUserOrFakeControls(){
        String header=BrowserHttp.transferHeader("/sample-app","SENSITIVE_VISIBLE_CANARY");
        assertTrue(header.contains("/sample-app/jakarta.faces.resource/images/llavor-header-white.jpg.xhtml?ln=llavor"));
        assertTrue(header.contains("/sample-app/jakarta.faces.resource/images/industrial-background.jpg.xhtml?ln=llavor"));
        assertFalse(header.contains("SENSITIVE_VISIBLE_CANARY"));assertFalse(header.contains("<button"));assertFalse(header.contains("<a "));
        assertTrue(BrowserHttp.TRANSFER_STYLE.contains("background:#0B1018"));assertTrue(BrowserHttp.TRANSFER_STYLE.contains("border-bottom:4px solid #F0052C"));
        assertTrue(BrowserHttp.TRANSFER_STYLE.contains("min-height:84px;padding:8px 16px"));assertTrue(BrowserHttp.TRANSFER_STYLE.contains("margin-left:auto"));
        assertFalse(BrowserHttp.TRANSFER_STYLE.contains("animation"));
    }

}
