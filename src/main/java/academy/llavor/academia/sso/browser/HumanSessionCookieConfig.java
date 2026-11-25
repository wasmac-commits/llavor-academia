package academy.llavor.academia.sso.browser;
import jakarta.servlet.*;
/** Startup config only, no JSF components, database or external credential lookup. */
public class HumanSessionCookieConfig implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent event){
        var cookie=event.getServletContext().getSessionCookieConfig();
        cookie.setName("LLAVOR_ACADEMIA_SESSION");cookie.setHttpOnly(true);cookie.setAttribute("SameSite","Lax");
        // Servlet 6: false still marks cookies Secure when created on an HTTPS request.
        cookie.setSecure(false);
    }
}
