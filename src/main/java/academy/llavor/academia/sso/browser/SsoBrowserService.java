package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
@ApplicationScoped
public class SsoBrowserService {
    @Inject academy.llavor.academia.web.https.TrustedProxyConfiguration publicHttp;
    static final String CLAIMED=SsoBrowserService.class.getName()+".claimed";
    public record Started(PendingBrowserContexts.Context context,String cookieValue,String portalFlowId){
        public String toString(){return "SsoStarted[redacted]";}
    }
    private BrowserDestinationService destinations;private PendingBrowserContexts contexts;private BrowserTokens tokens;private SsoHttpClient client;private Clock clock;
    protected SsoBrowserService(){}
    @Inject public SsoBrowserService(BrowserDestinationService destinations,PendingBrowserContexts contexts,BrowserTokens tokens,SsoHttpClient client){
        this(destinations,contexts,tokens,client,Clock.systemUTC());
    }
    SsoBrowserService(BrowserDestinationService destinations,PendingBrowserContexts contexts,BrowserTokens tokens,SsoHttpClient client,Clock clock){
        this.destinations=destinations;this.contexts=contexts;this.tokens=tokens;this.client=client;this.clock=clock;
    }
    public Started start(HttpServletRequest req,Map<String,String> form){
        version(form);BrowserTokens.hash(form.get("portalFlowId"));
        var destination=destinations.load(BrowserTokens.uuid(form.get("classroomId")));var settings=destination.settings();
        if(!settings.technical().academyId().equals(BrowserTokens.uuid(form.get("academyId"))))throw new BrowserFailure("IDENTITY_MISMATCH");
        BrowserHttp.request(req,settings.academyBaseUrl());BrowserHttp.origin(req,settings.portalOrigin());
        try{contexts.replaceBrowser(BrowserTokens.hash(BrowserHttp.cookie(req)));}catch(BrowserFailure ignored){}
        String binding=tokens.next();var context=contexts.issue(settings,BrowserTokens.hash(binding));
        return new Started(context,binding,form.get("portalFlowId"));
    }
    public java.net.URI logout(HumanSession human){var result=client.logout(human.authorization());return publicHttp!=null&&publicHttp.enabled()?java.net.URI.create(publicHttp.base("llavor-portal")+"/login.xhtml"):result;}
    public void enter(HttpServletRequest req,Map<String,String> form){
        version(form);BrowserTokens.hash(form.get("ticket"));UUID id=BrowserTokens.uuid(form.get("contextId"));
        var context=contexts.peek(id);var destination=destinations.load(context.settings().technical().classroomId());
        BrowserHttp.request(req,destination.settings().academyBaseUrl());BrowserHttp.origin(req,destination.settings().portalOrigin());
        contexts.claim(id,BrowserTokens.hash(BrowserHttp.cookie(req)),destination.settings());
        req.setAttribute(CLAIMED,Boolean.TRUE);
        var grant=client.consume(form.get("ticket"),context.bindingHash(),context.settings().technical().classroomId());
        var current=destinations.load(context.settings().technical().classroomId());
        if(!current.settings().equals(context.settings()))throw new BrowserFailure("DESTINATION_CHANGED");
        validateGrant(grant,current.settings());
        HumanSession human=new HumanSession(grant,clock.instant(),tokens.next(),current.settings().academyBaseUrl());
        HttpSession old=req.getSession(false);BrowserHttp.invalidate(old);
        HttpSession session=req.getSession(true);
        try{
            req.changeSessionId();session.setMaxInactiveInterval(1800);session.setAttribute(HumanSession.ATTRIBUTE,human);
        }catch(RuntimeException e){BrowserHttp.invalidate(session);throw new BrowserFailure("SESSION_UNAVAILABLE");}
    }
    static void validateGrant(SsoAuthorization grant,BrowserSettings settings){
        var expected=settings.technical();
        if(grant==null||grant.accessId()==null||grant.accessId().equals(new UUID(0,0))||grant.userId()<1
            ||!expected.portalId().equals(grant.portalId())||!expected.academyId().equals(grant.academyId())
            ||!expected.classroomId().equals(grant.classroomId()))throw new BrowserFailure("IDENTITY_MISMATCH");
    }
    private static void version(Map<String,String> form){if(!"1".equals(form.get("contractVersion")))throw new BrowserFailure("INVALID_REQUEST");}
}
