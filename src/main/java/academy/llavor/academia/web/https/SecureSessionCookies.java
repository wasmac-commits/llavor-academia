package academy.llavor.academia.web.https;
import jakarta.servlet.*;
/** Human sessions are HTTPS-only; affects this application, never the shared connector. */
public class SecureSessionCookies implements ServletContextListener {
 public void contextInitialized(ServletContextEvent event){event.getServletContext().getSessionCookieConfig().setSecure(true);}
}
