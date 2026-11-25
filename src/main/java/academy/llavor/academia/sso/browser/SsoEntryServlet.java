package academy.llavor.academia.sso.browser;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
/** Actual browser entry. No issue endpoint or diagnostic shortcut. */
public class SsoEntryServlet extends HttpServlet {
    public static final String PORTAL_CONTINUE="/sso/continue";
    @Inject private SsoBrowserService service;
    public SsoEntryServlet(){}
    SsoEntryServlet(SsoBrowserService service){this.service=service;}
    @Override protected void service(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        BrowserHttp.headers(res);
        if(!"POST".equals(req.getMethod())){res.setStatus(405);res.setHeader("Allow","POST");return;}
        String path=req.getServletPath();
        if(!(req.getContextPath()+path).equals(req.getRequestURI())||req.getPathInfo()!=null){res.setStatus(404);return;}
        try{
            switch(path){
                case "/sso/start" -> start(req,res);
                case "/sso/enter" -> {
                    var form=BrowserHttp.form(req,Set.of("contractVersion","contextId","ticket"));
                    try{service.enter(req,form);}
                    finally{if(Boolean.TRUE.equals(req.getAttribute(SsoBrowserService.CLAIMED)))BrowserHttp.bindingCookie(req,res,"",0);}
                    BrowserHttp.redirect(req,res,"/index.xhtml");
                }
                case "/sso/logout" -> logout(req,res);
                default -> res.setStatus(404);
            }
        }catch(RuntimeException failure){BrowserHttp.failure(res,path.equals("/sso/logout")?"LOGOUT":path.equals("/sso/start")?"START":"ENTER",failure);}
    }
    private void start(HttpServletRequest req,HttpServletResponse res)throws IOException{
        var form=BrowserHttp.form(req,Set.of("contractVersion","portalFlowId","academyId","classroomId"));
        String displayName=form.remove("uiDisplayName");
        var started=service.start(req,form);var context=started.context();var t=context.settings().technical();
        BrowserHttp.bindingCookie(req,res,started.cookieValue(),PendingBrowserContexts.TTL_SECONDS);
        String action=context.settings().portalPublicUrl().toASCIIString()+PORTAL_CONTINUE;
        // Navigation POST requires a tuple Origin. no-referrer would serialize it as null.
        // Coordinated browser contract adjustment 18.22: origin only, never paths or ticket.
        res.setHeader("Referrer-Policy","strict-origin");
        res.setHeader("Content-Security-Policy","default-src 'none'; script-src "+BrowserHttp.AUTO_POST_CSP+"; style-src "+BrowserHttp.TRANSFER_STYLE_CSP+"; img-src 'self'; form-action "+context.settings().portalOrigin()+"; base-uri 'none'; frame-ancestors 'none'");
        res.setContentType("text/html; charset=UTF-8");
        var out=res.getWriter();
        out.write("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Acceso al aula · Llavor</title><style>"+BrowserHttp.TRANSFER_STYLE+"</style></head><body>"+BrowserHttp.transferHeader(req.getContextPath(),displayName)+"<main><p role=\"status\">Accediendo a tu aula…</p><details><summary>Opciones de acceso</summary><p>Si el acceso no continúa, puedes continuar manualmente.</p><form id=\"sso-transfer\" method=\"post\" accept-charset=\"UTF-8\" action=\""+BrowserHttp.html(action)+"\">");
        Map<String,String> fields=new LinkedHashMap<>();fields.put("contractVersion","1");fields.put("portalFlowId",started.portalFlowId());
        fields.put("contextId",context.id().toString());fields.put("portalInstanceId",t.portalId().toString());fields.put("academyId",t.academyId().toString());
        fields.put("classroomId",t.classroomId().toString());fields.put("browserBindingHash",context.bindingHash());
        if(displayName!=null)fields.put("uiDisplayName",displayName);
        for(var field:fields.entrySet())out.write("<input type=\"hidden\" name=\""+field.getKey()+"\" value=\""+BrowserHttp.html(field.getValue())+"\">");
        out.write("<button type=\"submit\">Continuar en Portal</button></form></details><noscript><p>JavaScript está deshabilitado. Abre Opciones de acceso para continuar.</p></noscript><script>"+BrowserHttp.AUTO_POST_SCRIPT+"</script></main></body></html>");
    }
    private void logout(HttpServletRequest req,HttpServletResponse res)throws IOException{
        var form=BrowserHttp.form(req,Set.of("csrf"));HttpSession session=req.getSession(false);
        if(session==null||!(session.getAttribute(HumanSession.ATTRIBUTE) instanceof HumanSession human))throw new BrowserFailure("SESSION_REQUIRED");
        BrowserHttp.request(req,human.academyBaseUrl());BrowserHttp.origin(req,BrowserSettings.origin(human.academyBaseUrl()));
        BrowserTokens.hash(form.get("csrf"));
        if(!BrowserTokens.same(human.csrf(),form.get("csrf")))throw new BrowserFailure("CSRF_REJECTED");
        java.net.URI login;
        try{login=service.logout(human);if(login==null)throw new BrowserFailure("CONFIGURATION_UNAVAILABLE");}
        catch(RuntimeException failure){
            BrowserHttp.failure(res,"LOGOUT",failure);res.setStatus(503);return;
        }finally{
            BrowserHttp.invalidate(session);BrowserHttp.bindingCookie(req,res,"",0);
        }
        BrowserHttp.headers(res);res.setStatus(303);res.setHeader("Location",login.toASCIIString());
    }
}
