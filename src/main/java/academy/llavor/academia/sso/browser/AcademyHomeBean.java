package academy.llavor.academia.sso.browser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.*;
import jakarta.servlet.http.HttpServletRequest;
/** No lifecycle initialization or component access; displays only the filter's request projection. */
@Named("academyHome") @RequestScoped
public class AcademyHomeBean {
    @Inject private HttpServletRequest request;
    private HumanView view(){Object value=request.getAttribute(BrowserHttp.VIEW);return value instanceof HumanView v?v:null;}
    public String getDisplayName(){var v=view();return v==null?"":v.displayName();}
    public String getAcademyCode(){var v=view();return v==null?"":v.academyCode();}
    public String getClassroomCode(){var v=view();return v==null?"":v.classroomCode();}
    public String getClassroomName(){var v=view();return v==null?"":v.classroomName();}
    public String getCsrf(){var v=view();return v==null?"":v.csrf();}
}
