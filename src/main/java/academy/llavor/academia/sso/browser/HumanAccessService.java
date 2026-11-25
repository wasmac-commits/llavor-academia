package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.*;
import java.time.*;
@ApplicationScoped
public class HumanAccessService {
    @Inject SsoConfiguration configuration;
    public java.net.URI portalLoginUrl(){return configuration.portalLoginUrl();}
    private BrowserDestinationService destinations;private SsoHttpClient client;private Clock clock;
    protected HumanAccessService(){}
    @Inject public HumanAccessService(BrowserDestinationService destinations,SsoHttpClient client){this(destinations,client,Clock.systemUTC());}
    HumanAccessService(BrowserDestinationService destinations,SsoHttpClient client,Clock clock){this.destinations=destinations;this.client=client;this.clock=clock;}
    public void check(HttpServletRequest req){
        HttpSession session=req.getSession(false);
        if(session==null)throw new BrowserFailure("SESSION_REQUIRED");
        Object value=session.getAttribute(HumanSession.ATTRIBUTE);
        if(!(value instanceof HumanSession human)||!human.valid(clock.instant()))throw new BrowserFailure("SESSION_EXPIRED");
        if(req.getAttribute(BrowserHttp.CHECKED)==human)return; // Same dispatch/request only, never a session cache.
        var destination=destinations.load(human.authorization().classroomId());
        BrowserHttp.request(req,destination.settings().academyBaseUrl());
        if(!human.academyBaseUrl().equals(destination.settings().academyBaseUrl()))throw new BrowserFailure("DESTINATION_CHANGED");
        SsoBrowserService.validateGrant(human.authorization(),destination.settings());
        var checked=client.revalidate(human.authorization());
        if(checked==null||!human.authorization().equals(checked.authorization()))throw new BrowserFailure("INVALID_REVALIDATION");
        // A local disable/destination change during HTTP must also deny this request.
        var after=destinations.load(human.authorization().classroomId());
        if(!after.settings().equals(destination.settings()))throw new BrowserFailure("DESTINATION_CHANGED");
        if(req.getSession(false)!=session||session.getAttribute(HumanSession.ATTRIBUTE)!=human||!human.valid(clock.instant()))
            throw new BrowserFailure("SESSION_CHANGED");
        human.touch(clock.instant());
        req.setAttribute(BrowserHttp.VIEW,new HumanView(human.authorization().displayName(),after.academyCode(),after.classroomCode(),after.classroomName(),human.csrf()));
        req.setAttribute(BrowserHttp.CHECKED,human);
    }
}
