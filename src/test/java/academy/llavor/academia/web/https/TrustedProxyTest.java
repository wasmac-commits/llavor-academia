package academy.llavor.academia.web.https;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TrustedProxyTest {
 @TempDir Path directory;
 static final String TOKEN="a".repeat(64);
 TrustedProxyConfiguration config()throws Exception{
  var file=directory.resolve("proxy.json");Files.writeString(file,"{\"version\":1,\"origin\":\"https://portal.example.test:8443\",\"portalBaseUrl\":\"https://portal.example.test:8443/llavor-portal\",\"academiaBaseUrl\":\"https://academy.example.test:8443/llavor-academia\",\"trustedPeers\":[\"192.0.2.10\"],\"directHttpPeers\":[\"192.0.2.10\"],\"proxyToken\":\""+TOKEN+"\"}");
  var config=new TrustedProxyConfiguration();config.configPath=()->file.toString();return config;
 }
 Map<String,String> headers(){return new HashMap<>(Map.of("Host","portal.example.test:8443","X-Llavor-Proxy-Token",TOKEN,"X-Forwarded-Proto","https","X-Forwarded-Host","portal.example.test","X-Forwarded-Port","8443","X-Forwarded-For","198.51.100.23"));}
 HttpServletRequest request(Map<String,String> headers){var req=mock(HttpServletRequest.class);when(req.getRemoteAddr()).thenReturn("192.0.2.10");when(req.getContextPath()).thenReturn("/llavor-academia");when(req.getRequestURI()).thenReturn("/llavor-academia/index.xhtml");when(req.getHeader(anyString())).thenAnswer(i->headers.get(i.getArgument(0)));when(req.getHeaders(anyString())).thenAnswer(i->{String v=headers.get(i.getArgument(0));return Collections.enumeration(v==null?List.of():List.of(v));});return req;}
 @Test void disabledByDefaultAndExplicitPolicyWorksWithoutAnEnvironmentBranch()throws Exception{var absent=new TrustedProxyConfiguration();assertFalse(absent.enabled());var config=config();assertTrue(config.enabled());assertEquals("academy.example.test",config.base("llavor-academia").getHost());assertEquals("portal.example.test",config.base("llavor-portal").getHost());}
 @Test void authenticatedProxyNormalizesHttpsAndProtectsItsSecret()throws Exception{var filter=new TrustedProxyFilter();filter.config=config();var req=request(headers());var res=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);filter.doFilter(req,res,chain);var capture=org.mockito.ArgumentCaptor.forClass(ServletRequest.class);verify(chain).doFilter(capture.capture(),any());var wrapped=(HttpServletRequest)capture.getValue();assertTrue(wrapped.isSecure());assertEquals("https",wrapped.getScheme());assertEquals(8443,wrapped.getServerPort());assertEquals("portal.example.test",wrapped.getServerName());assertEquals("198.51.100.23",wrapped.getRemoteAddr());assertNull(wrapped.getHeader("X-Llavor-Proxy-Token"));assertFalse(wrapped.getHeaders("X-Llavor-Proxy-Token").hasMoreElements());assertFalse(filter.config.settings().toString().contains(TOKEN));}
 @Test void untrustedPeerAndLocalSpoofingNeverReachTheApplication()throws Exception{for(int scenario=0;scenario<5;scenario++){var h=headers();var req=request(h);switch(scenario){case 0->when(req.getRemoteAddr()).thenReturn("198.51.100.23");case 1->h.put("X-Llavor-Proxy-Token","b".repeat(64));case 2->h.put("Host","attacker.example.test:8443");case 3->h.put("Forwarded","proto=https");case 4->when(req.getHeaders("X-Llavor-Proxy-Token")).thenAnswer(i->Collections.enumeration(List.of(TOKEN,TOKEN)));}var filter=new TrustedProxyFilter();filter.config=config();var res=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);filter.doFilter(req,res,chain);verify(res).sendError(403);verifyNoInteractions(chain);}}
 @Test void directHttpRequiresItsOwnExplicitPeerPolicy()throws Exception{var filter=new TrustedProxyFilter();filter.config=config();var req=request(new HashMap<>());var res=mock(HttpServletResponse.class);var chain=mock(FilterChain.class);when(req.getRemoteAddr()).thenReturn("198.51.100.23");filter.doFilter(req,res,chain);verify(res).sendError(eq(426),anyString());verifyNoInteractions(chain);when(req.getRemoteAddr()).thenReturn("192.0.2.10");filter.doFilter(req,res,chain);verify(chain).doFilter(req,res);}
 @Test void onlyThisApplicationGetsSecureSessionCookies(){var listener=new SecureSessionCookies();var context=mock(ServletContext.class);var cookies=mock(SessionCookieConfig.class);when(context.getSessionCookieConfig()).thenReturn(cookies);listener.contextInitialized(new ServletContextEvent(context));verify(cookies).setSecure(true);}
 @Test void publicHttpOriginIsRejectedEvenWithAnExplicitPolicy()throws Exception{var config=config();var file=directory.resolve("proxy.json");Files.writeString(file,Files.readString(file).replace("https://portal.example.test:8443","http://portal.example.test:8443"));assertThrows(IllegalStateException.class,config::enabled);}
}
