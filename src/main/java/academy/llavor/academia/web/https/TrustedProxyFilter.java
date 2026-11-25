package academy.llavor.academia.web.https;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
/** Explicit trusted peer AND shared-secret authentication; no header trust by default. */
public class TrustedProxyFilter implements Filter {
 @Inject TrustedProxyConfiguration config;
 static String one(HttpServletRequest req,String name){var values=req.getHeaders(name);if(values==null||!values.hasMoreElements())return null;String value=values.nextElement();return values.hasMoreElements()?null:value;}
 public void doFilter(ServletRequest input,ServletResponse output,FilterChain chain)throws IOException,ServletException{
  var req=(HttpServletRequest)input;var res=(HttpServletResponse)output;
  TrustedProxyConfiguration.Settings settings;try{settings=config.settings();}catch(RuntimeException invalid){res.sendError(503);return;}
  if(settings==null){chain.doFilter(req,res);return;}
  String supplied=one(req,"X-Llavor-Proxy-Token");
  boolean proxyHeaders=req.getHeader("X-Llavor-Proxy-Token")!=null||req.getHeader("X-Forwarded-Proto")!=null||req.getHeader("X-Forwarded-Host")!=null||req.getHeader("X-Forwarded-For")!=null||req.getHeader("Forwarded")!=null;
  if(!proxyHeaders){if(!req.isSecure()&&!TrustedProxyConfiguration.containsPeer(settings.directHttpPeers(),req.getRemoteAddr())){res.sendError(426,"Use the configured HTTPS address");return;}chain.doFilter(req,res);return;}
  String host=settings.origin().getHost();int port=settings.origin().getPort()<0?443:settings.origin().getPort();String remote=one(req,"X-Forwarded-For");
  if(!TrustedProxyConfiguration.containsPeer(settings.trustedPeers(),req.getRemoteAddr())||!config.authentic(supplied)||!"https".equals(one(req,"X-Forwarded-Proto"))||!host.equals(one(req,"X-Forwarded-Host"))||!Integer.toString(port).equals(one(req,"X-Forwarded-Port"))||! (host+":"+port).equals(one(req,"Host"))||req.getHeader("Forwarded")!=null||remote==null||!remote.matches("[0-9a-fA-F:.]{2,64}")){res.sendError(403);return;}
  var wrapped=new HttpServletRequestWrapper(req){
   public boolean isSecure(){return true;}public String getScheme(){return "https";}public String getServerName(){return host;}public int getServerPort(){return port;}public String getRemoteAddr(){return remote;}
   public StringBuffer getRequestURL(){return new StringBuffer(settings.origin().toASCIIString()+getRequestURI());}
   public java.util.Enumeration<String> getHeaderNames(){return java.util.Collections.enumeration(java.util.Collections.list(super.getHeaderNames()).stream().filter(name->!name.equalsIgnoreCase("X-Llavor-Proxy-Token")).toList());}
   public java.util.Enumeration<String> getHeaders(String name){return "X-Llavor-Proxy-Token".equalsIgnoreCase(name)?java.util.Collections.emptyEnumeration():super.getHeaders(name);}
   public String getHeader(String name){return "X-Llavor-Proxy-Token".equalsIgnoreCase(name)?null:super.getHeader(name);}
  };
  chain.doFilter(wrapped,new HttpServletResponseWrapper(res){public void sendRedirect(String target)throws IOException{
   if(target.startsWith(req.getContextPath()+"/"))target=settings.origin().toASCIIString()+target;
   super.sendRedirect(target);
  }});
 }
}
