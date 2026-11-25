package academy.llavor.academia.sso.browser;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.*;
/** Closed form parser and browser boundary. No request body/header/exception message logging. */
final class BrowserHttp {
    // Fixed, data-free script: CSP authorizes these exact UTF-8 bytes only.
    // Corporate transfer shell; native details provides manual recovery without JS.
    static final String TRANSFER_STYLE = "html,body{margin:0;min-height:100%;background:#0B1018;color:#fff;font:15px/1.6 system-ui,-apple-system,\"Segoe UI\",sans-serif}*{box-sizing:border-box}body{min-height:100dvh;display:flex;flex-direction:column}.llavor-transfer-background{position:fixed;inset:96px 0 0;z-index:0;pointer-events:none;background:#0B1018}.llavor-transfer-background img{width:100%;height:100%;object-fit:cover;display:block}.llavor-transfer-background:after{content:\"\";position:absolute;inset:0;background:linear-gradient(90deg,#0B1018eb,#0B1018b8 65%,#0B1018d9),linear-gradient(0deg,#0B1018,transparent 70%)}.llavor-topbar{position:relative;z-index:2;min-height:96px;background:#fff;color:#16212D;border-bottom:4px solid #F0052C;display:flex;align-items:center;gap:12px;padding:12px 28px}.llavor-brand-link{display:flex;align-items:center;min-width:0}.llavor-header-logo{display:block;width:204px;height:68px;object-fit:contain;max-width:100%}main{position:relative;z-index:1;flex:1;display:grid;place-content:center;gap:16px;width:100%;max-width:520px;margin:auto;padding:32px 20px;text-align:center}p{margin:0;color:#AEBBC9;font-size:13px}p[role=status]{font-size:16px;color:#fff;font-weight:600;padding:20px 24px;background:#16212Df5;border:1px solid #415064;border-radius:12px}details{font-size:13px;color:#AEBBC9;border:1px solid #415064;border-radius:10px;padding:12px 18px;background:#16212Df5}summary{cursor:pointer;min-height:28px}details>p{margin-top:12px}form{margin-top:16px}button{min-height:44px;padding:10px 24px;border:1px solid #F0052C;border-radius:8px;background:#F0052C;color:#fff;font:600 14px/1.5 system-ui,-apple-system,\"Segoe UI\",sans-serif;cursor:pointer}button:hover{background:#cc0026}summary:focus-visible,button:focus-visible{outline:3px solid #fb7185;outline-offset:3px}noscript p{padding:12px 0}@media(max-width:1023px){.llavor-topbar{min-height:84px;padding:8px 16px;flex-wrap:nowrap}.llavor-brand-link{margin-left:auto;justify-content:flex-end}.llavor-header-logo{width:180px;height:60px;max-width:calc(100vw - 104px)}.llavor-transfer-background{top:84px}main{padding:24px 16px}}";
    static final String TRANSFER_STYLE_CSP = "'sha256-tetnqlYCrTcdL28iwIzNEY90QMCwgBOnNRNkXlZuE+Q='";
    // Cosmetic hint only: never used as an authenticated identity or authorization input.
    static String displayName(String value){
        if(value==null)return "";
        return value.codePoints().filter(c->!Character.isISOControl(c)).limit(100)
            .collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
    }
    /** Presentation only: no session identity or nonfunctional controls during transfer. */
    static String transferHeader(String contextPath,String name){
        String resources=contextPath+"/jakarta.faces.resource/images/";
        return "<div class=\"llavor-transfer-background\" aria-hidden=\"true\"><img src=\""+html(resources+"industrial-background.jpg.xhtml?ln=llavor")+"\" alt=\"\"></div><header class=\"llavor-topbar\"><div class=\"llavor-brand-link\"><img src=\""+html(resources+"llavor-header-white.jpg.xhtml?ln=llavor")+"\" alt=\"Llavor Academy\" class=\"llavor-header-logo\" width=\"204\" height=\"68\"></div></header>";
    }
    static final String AUTO_POST_SCRIPT = "(()=>{const form=document.getElementById(\"sso-transfer\");form.addEventListener(\"submit\",event=>{if(form.dataset.submitted===\"true\"){event.preventDefault();return;}form.dataset.submitted=\"true\";});const navigation=performance.getEntriesByType(\"navigation\")[0];if(!navigation||navigation.type!==\"back_forward\")form.requestSubmit();})();";
    static final String AUTO_POST_CSP = "'sha256-qx2nBu0LvUqLoira975h12RvGwJjbuN5B2xSEy69ccU='";
    static final String BINDING="LLAVOR_ACADEMIA_SSO_BINDING";
    static final String VIEW=BrowserHttp.class.getName()+".view",CHECKED=BrowserHttp.class.getName()+".checked";
    private static final Logger LOG=Logger.getLogger("academy.llavor.academia.sso.browser");
    static Map<String,String> form(HttpServletRequest req,Set<String> fields)throws IOException{
        String type=req.getContentType();
        if(!"POST".equals(req.getMethod())||req.getQueryString()!=null||type==null
            ||!type.toLowerCase(Locale.ROOT).matches("application/x-www-form-urlencoded(?:\\s*;\\s*charset=utf-8)?"))throw new BrowserFailure("INVALID_REQUEST");
        byte[] bytes=req.getInputStream().readNBytes(2049);
        try{
            if(bytes.length>2048)throw new BrowserFailure("FORM_LIMIT");
            for(byte b:bytes)if(b<32||b>126)throw new BrowserFailure("INVALID_REQUEST");
            var values=new HashMap<String,String>();
            for(String pair:new String(bytes,StandardCharsets.US_ASCII).split("&",-1)){
                String[] parts=pair.split("=",2);if(parts.length!=2)throw new BrowserFailure("INVALID_REQUEST");
                String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8),value=URLDecoder.decode(parts[1],StandardCharsets.UTF_8);
                if((!fields.contains(key)&&!(fields.contains("portalFlowId")&&"uiDisplayName".equals(key)))||values.putIfAbsent(key,value)!=null)throw new BrowserFailure("INVALID_REQUEST");
            }
            var required=new HashSet<>(values.keySet());required.remove("uiDisplayName");
            if(values.containsKey("uiDisplayName")&&!values.get("uiDisplayName").equals(displayName(values.get("uiDisplayName"))))throw new BrowserFailure("INVALID_REQUEST");
            if(!required.equals(fields))throw new BrowserFailure("INVALID_REQUEST");return values;
        }catch(IllegalArgumentException e){throw new BrowserFailure("INVALID_REQUEST");}
        finally{Arrays.fill(bytes,(byte)0);}
    }
    static void request(HttpServletRequest req,URI own){
        try{
            String scheme=req.isSecure()?"https":"http";
            String host=req.getServerName();if(host.contains(":")&&!host.startsWith("["))host="["+host+"]";
            URI received=URI.create(scheme+"://"+host+":"+req.getServerPort());
            if(!BrowserSettings.origin(received).equals(BrowserSettings.origin(own))||!Objects.equals(req.getContextPath(),own.getRawPath()))throw new BrowserFailure("ORIGIN_MISMATCH");
            if(!req.isSecure()&&!Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(req.getRemoteAddr()))throw new BrowserFailure("LOCAL_TRANSPORT_REQUIRED");
        }catch(BrowserFailure e){throw e;}catch(RuntimeException e){throw new BrowserFailure("ORIGIN_MISMATCH");}
    }
    static void origin(HttpServletRequest req,String expected){
        var headers=req.getHeaders("Origin");
        if(headers==null||!headers.hasMoreElements())throw new BrowserFailure("ORIGIN_REQUIRED");
        String value=headers.nextElement();if(headers.hasMoreElements()||!expected.equals(value))throw new BrowserFailure("ORIGIN_MISMATCH");
    }
    static String cookie(HttpServletRequest req){
        String value=null;
        if(req.getCookies()!=null)for(Cookie cookie:req.getCookies())if(BINDING.equals(cookie.getName())){
            if(value!=null)throw new BrowserFailure("AMBIGUOUS_BINDING");value=cookie.getValue();
        }
        if(value==null)throw new BrowserFailure("BINDING_REQUIRED");BrowserTokens.hash(value);return value;
    }
    static void bindingCookie(HttpServletRequest req,HttpServletResponse res,String value,int age){
        var cookie=new Cookie(BINDING,value);cookie.setHttpOnly(true);cookie.setSecure(req.isSecure());
        cookie.setPath(req.getContextPath()+"/sso");cookie.setMaxAge(age);cookie.setAttribute("SameSite",req.isSecure()?"None":"Lax");res.addCookie(cookie);
    }
    static void headers(HttpServletResponse res){
        res.setHeader("Cache-Control","no-store");res.setHeader("Pragma","no-cache");res.setHeader("Referrer-Policy","no-referrer");
        res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("X-Frame-Options","DENY");
    }
    static void redirect(HttpServletRequest req,HttpServletResponse res,String path){headers(res);res.setStatus(303);res.setHeader("Location",req.getContextPath()+path);}
    static String html(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    static String trace(String action,RuntimeException failure){
        String code=failure instanceof BrowserFailure b?b.code():failure instanceof academy.llavor.academia.sso.SsoClientFailure s?s.getCode().name():"UNAVAILABLE";
        String ref=UUID.randomUUID().toString();LOG.log(Level.WARNING,"SSO_BROWSER action={0} code={1} ref={2}",new Object[]{action,code,ref});return ref;
    }
    static void failure(HttpServletResponse res,String action,RuntimeException failure)throws IOException{
        String ref=trace(action,failure);
        headers(res);res.setStatus(403);res.setContentType("text/html; charset=UTF-8");
        res.getWriter().write("<!doctype html><html lang=\"es\"><meta charset=\"utf-8\"><title>Acceso no disponible</title><p>No se ha podido completar el acceso. Vuelve al selector de aulas de Portal para iniciar un acceso nuevo.</p><p>Referencia: "+ref+"</p></html>");
    }
    static void invalidate(HttpSession session){if(session!=null)try{session.invalidate();}catch(IllegalStateException ignored){}}
}
