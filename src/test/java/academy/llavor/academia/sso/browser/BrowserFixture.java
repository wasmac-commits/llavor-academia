package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
final class BrowserFixture {
    static final UUID A=UUID.fromString("11111111-1111-4111-8111-111111111111"),P=UUID.fromString("22222222-2222-4222-8222-222222222222"),
        C=UUID.fromString("33333333-3333-4333-8333-333333333333"),ACCESS=UUID.fromString("44444444-4444-4444-8444-444444444444");
    static final Instant NOW=Instant.parse("2026-01-01T12:00:00Z");
    static final String BINDING="A".repeat(43),FLOW="B".repeat(42)+"A",TICKET="D".repeat(42)+"A",CSRF="C".repeat(42)+"A";
    static BrowserSettings settings(boolean secure){
        String portal=secure?"https://portal.example.test/llavor-portal":"http://localhost:8080/llavor-portal";
        String academy=secure?"https://academy.example.test/llavor-academia":"http://localhost:8080/llavor-academia";
        return new BrowserSettings(new SsoSettings(A,P,C,URI.create(portal),"S01",Duration.ofSeconds(3),Duration.ofSeconds(15)),URI.create(academy));
    }
    static BrowserDestination destination(boolean secure){return new BrowserDestination(settings(secure),"ACADEMY_TEST","ROOM_TEST","Synthetic classroom");}
    static SsoAuthorization grant(){return new SsoAuthorization(ACCESS,P,1,A,C,NOW,NOW.plusSeconds(3600),"Synthetic user");}
    static Map<String,String> start(){return Map.of("contractVersion","1","portalFlowId",FLOW,"academyId",A.toString(),"classroomId",C.toString());}
    static Map<String,String> enter(UUID id){return Map.of("contractVersion","1","contextId",id.toString(),"ticket",TICKET);}
    static class MutableClock extends Clock {
        Instant value=NOW;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return value;}
    }
    static class Request {
        final HttpServletRequest req=mock(HttpServletRequest.class);
        final Map<String,Object> attributes=new HashMap<>();
        final AtomicReference<HttpSession> current=new AtomicReference<>();
        final Map<HttpSession,Map<String,Object>> sessions=new HashMap<>();
        Request(boolean secure,String path)throws Exception{
            when(req.getMethod()).thenReturn("POST");when(req.getContextPath()).thenReturn("/llavor-academia");
            when(req.getRequestURI()).thenReturn("/llavor-academia"+path);when(req.getServletPath()).thenReturn(path);
            when(req.isSecure()).thenReturn(secure);when(req.getServerName()).thenReturn(secure?"academy.example.test":"localhost");
            when(req.getServerPort()).thenReturn(secure?443:8080);when(req.getRemoteAddr()).thenReturn("127.0.0.1");
            origin(secure?"https://portal.example.test":"http://localhost:8080");
            when(req.getCookies()).thenReturn(new Cookie[]{new Cookie(BrowserHttp.BINDING,BINDING)});
            doAnswer(i->{attributes.put(i.getArgument(0),i.getArgument(1));return null;}).when(req).setAttribute(anyString(),any());
            when(req.getAttribute(anyString())).thenAnswer(i->attributes.get(i.getArgument(0)));
            when(req.getSession(anyBoolean())).thenAnswer(i->{if(current.get()==null&&(Boolean)i.getArgument(0))current.set(session());return current.get();});
            when(req.changeSessionId()).thenReturn("rotated-id");
        }
        HttpSession session(){
            HttpSession s=mock(HttpSession.class);Map<String,Object> data=new HashMap<>();sessions.put(s,data);
            when(s.getAttribute(anyString())).thenAnswer(i->data.get(i.getArgument(0)));
            doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(s).setAttribute(anyString(),any());
            doAnswer(i->{data.clear();current.compareAndSet(s,null);return null;}).when(s).invalidate();return s;
        }
        void human(HumanSession human){var s=session();current.set(s);sessions.get(s).put(HumanSession.ATTRIBUTE,human);}
        void origin(String origin){when(req.getHeaders("Origin")).thenAnswer(i->Collections.enumeration(List.of(origin)));}
        void body(String body)throws Exception{
            when(req.getContentType()).thenReturn("application/x-www-form-urlencoded; charset=UTF-8");
            byte[] raw=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            when(req.getInputStream()).thenAnswer(i->{
                var in=new ByteArrayInputStream(raw);
                return new ServletInputStream(){
                    public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}
                    public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}
                };
            });
        }
    }
}
