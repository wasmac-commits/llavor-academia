package academy.llavor.academia.sso.browser;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
/** Closed human routes; exact technical paths retain their existing K01 authenticator. */
public class HumanAccessFilter implements Filter {
    @Inject private HumanAccessService access;
    public HumanAccessFilter(){}
    HumanAccessFilter(HumanAccessService access){this.access=access;}
    static boolean technical(String path){
        return path.matches("/internal/v1/academies/[^/]+/identity")
            ||path.matches("/internal/v1/academies/[^/]+/classrooms/[^/]+(?:/provisioning-operations/[^/]+)?");
    }
    static boolean resource(String path){
        return java.util.Set.of("/jakarta.faces.resource/theme.css.xhtml",
            "/jakarta.faces.resource/components.css.xhtml", "/jakarta.faces.resource/core.js.xhtml",
            "/jakarta.faces.resource/components.js.xhtml", "/jakarta.faces.resource/jquery/jquery.js.xhtml",
            "/jakarta.faces.resource/jquery/jquery-plugins.js.xhtml", "/jakarta.faces.resource/locales/locale-es.js.xhtml",
            "/jakarta.faces.resource/locales/locale-ca.js.xhtml", "/jakarta.faces.resource/jakarta.faces.js.xhtml",
            "/jakarta.faces.resource/css/academia.css.xhtml",
            "/jakarta.faces.resource/css/academy-shell.css.xhtml",
            "/jakarta.faces.resource/css/platform-header.css.xhtml",
            "/jakarta.faces.resource/css/academy-navigation.css.xhtml",
            "/jakarta.faces.resource/images/llavor-header-white.jpg.xhtml",
            "/jakarta.faces.resource/js/academy-shell.js.xhtml",
            "/jakarta.faces.resource/js/header-controls.js.xhtml",
            "/jakarta.faces.resource/images/industrial-background.jpg.xhtml",
            "/jakarta.faces.resource/css/language-selector.css.xhtml", "/resources/llavor/css/language-selector.css",
            "/jakarta.faces.resource/css/llavor-theme.css.xhtml",
            "/jakarta.faces.resource/images/llavor-academy-horizontal.png.xhtml",
            "/resources/llavor/css/academia.css", "/resources/llavor/css/llavor-theme.css",
            "/resources/llavor/images/llavor-academy-horizontal.png",
            "/jakarta.faces.resource/primeicons/primeicons.css.xhtml",
            "/jakarta.faces.resource/primeicons/primeicons.woff2.xhtml",
            "/jakarta.faces.resource/primeicons/primeicons.woff.xhtml",
            "/jakarta.faces.resource/primeicons/primeicons.ttf.xhtml",
            "/jakarta.faces.resource/primeicons/primeicons.eot.xhtml",
            "/jakarta.faces.resource/primeicons/primeicons.svg.xhtml").contains(path);
    }
    @Override public void doFilter(ServletRequest input,ServletResponse output,FilterChain chain)throws IOException,ServletException{
        if(!(input instanceof HttpServletRequest req)||!(output instanceof HttpServletResponse res))return;
        String path=req.getServletPath();
        if(req.getPathInfo()!=null)path+=req.getPathInfo();
        if(path==null||!(req.getContextPath()+path).equals(req.getRequestURI())){res.setStatus(404);return;}
        BrowserHttp.headers(res);
        if(path.contains("%")||path.contains(";")||path.contains("\\")||path.contains("//")||path.contains("..")){res.setStatus(404);return;}
        if(technical(path)){chain.doFilter(req,res);return;}
        boolean read="GET".equals(req.getMethod())||"HEAD".equals(req.getMethod());
        if(resource(path)&&read){
            String query=req.getQueryString();
            String library=path.equals("/jakarta.faces.resource/theme.css.xhtml")?"primefaces-saga-blue":
                (path.startsWith("/jakarta.faces.resource/primeicons/")||java.util.Set.of("/jakarta.faces.resource/components.css.xhtml","/jakarta.faces.resource/core.js.xhtml","/jakarta.faces.resource/components.js.xhtml","/jakarta.faces.resource/jquery/jquery.js.xhtml","/jakarta.faces.resource/jquery/jquery-plugins.js.xhtml","/jakarta.faces.resource/locales/locale-es.js.xhtml","/jakarta.faces.resource/locales/locale-ca.js.xhtml").contains(path))?"primefaces":path.equals("/jakarta.faces.resource/jakarta.faces.js.xhtml")?"jakarta.faces":"llavor";
            if(path.startsWith("/jakarta.faces.resource/")){
                if(query==null||!query.matches("ln="+library+"(?:&v=[A-Za-z0-9._-]{1,40})?")){res.setStatus(404);return;}
            }else if(query!=null){res.setStatus(404);return;}
            chain.doFilter(req,res);return;
        }
        if(path.equals("/access-required.xhtml")&&read&&req.getQueryString()==null){login(req,res);return;}
        if(path.equals("/sso/start")||path.equals("/sso/enter")||path.equals("/sso/logout")){
            if(!"POST".equals(req.getMethod())){res.setHeader("Allow","POST");res.setStatus(405);return;}
            if(req.getQueryString()!=null){res.setStatus(400);return;}
            chain.doFilter(req,res);return;
        }
        if(!path.equals("/")&&!path.equals("/index.xhtml")){res.setStatus(404);return;}
        // The new JSF selector submits to this authenticated view. Its bean checks
        // session CSRF and published language; the existing access check still runs.
        boolean languagePost=path.equals("/index.xhtml")&&"POST".equals(req.getMethod());
        if((!read&&!languagePost)||req.getQueryString()!=null){res.setStatus(403);return;}
        try{access.check(req);}
        catch(RuntimeException failure){
            if(failure instanceof academy.llavor.academia.sso.SsoClientFailure transientFailure
                &&java.util.Set.of(academy.llavor.academia.sso.SsoClientFailure.Code.REMOTE_UNAVAILABLE,academy.llavor.academia.sso.SsoClientFailure.Code.TIMEOUT).contains(transientFailure.getCode())){
                // Keep the session and local language; access still fails closed, with no offline grant cache.
                BrowserHttp.trace("REVALIDATE",failure);res.setHeader("Retry-After","5");res.setStatus(503);return;
            }
            BrowserHttp.invalidate(req.getSession(false));
            BrowserHttp.trace("REVALIDATE",failure);
            // No supplied values or exception trace; no offline authorization.
            login(req,res);return;
        }
        // Only the authenticated page containing POST logout. no-referrer would
        // force Origin:null on navigation POST and make the strict logout fail.
        res.setHeader("Referrer-Policy","strict-origin");
        chain.doFilter(req,res);
    }
    private void login(HttpServletRequest req,HttpServletResponse res){
        try{
            var destination=access.portalLoginUrl();
            if(destination==null)throw new BrowserFailure("CONFIGURATION_UNAVAILABLE");
            res.setStatus(303);res.setHeader("Location",destination.toASCIIString());
        }catch(RuntimeException failure){BrowserHttp.trace("LOGIN_DESTINATION",failure);res.setStatus(503);}
    }

}
