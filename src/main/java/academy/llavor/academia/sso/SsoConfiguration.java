package academy.llavor.academia.sso;
import academy.llavor.academia.parameter.dto.ParameterReadResult;
import academy.llavor.academia.parameter.service.*;
import academy.llavor.academia.parameter.entity.ParameterType;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.provisioning.config.ExpectedInstallationIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import static academy.llavor.academia.sso.SsoClientFailure.Code.*;
/** Read-only independent SSO settings. No seed, writes, startup work or configuration/credential cache. */
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class SsoConfiguration {
    public static final String PREFIX="llavor.academia.sso.";
    public static final List<String> KEYS=List.of("enabled","portal-base-url","active-key-id","allow-loopback-http",
        "connect-timeout-seconds","request-timeout-seconds","environment").stream().map(k->PREFIX+k).toList();
    @Inject academy.llavor.academia.web.https.TrustedProxyConfiguration publicHttp;
    private ParameterConfigurationService parameters;private AcademyInstanceRepository identity;
    private LocalClassroomRepository classrooms;private ExpectedInstallationIdentity pin;
    protected SsoConfiguration(){}
    @Inject public SsoConfiguration(ParameterConfigurationService parameters,AcademyInstanceRepository identity,
            LocalClassroomRepository classrooms,ExpectedInstallationIdentity pin){
        this.parameters=parameters;this.identity=identity;this.classrooms=classrooms;this.pin=pin;
    }
    /** Login destination is available even when SSO is disabled or there is no human session. */
    public URI portalLoginUrl(){
        if(publicHttp!=null&&publicHttp.enabled())return URI.create(publicHttp.base("llavor-portal")+"/login.xhtml");
        try{
            var values=parameters.readBatch(List.of(PREFIX+"portal-base-url",PREFIX+"environment",PREFIX+"allow-loopback-http"));
            String environment=optional(values,"environment",String.class,"PRODUCTION");
            if(!Set.of("PRODUCTION","DEVELOPMENT").contains(environment))throw invalid();
            URI base=required(values,"portal-base-url",URI.class);
            validateUrl(base,"DEVELOPMENT".equals(environment),optional(values,"allow-loopback-http",Boolean.class,false));
            return URI.create(base.toASCIIString()+"/login.xhtml");
        }catch(SsoClientFailure e){throw e;}catch(RuntimeException e){throw invalid();}
    }
    public SsoSettings loadEnabled(UUID classroom){try{
        SsoValidation.uuid(classroom);var values=parameters.readBatch(KEYS);
        if(!optional(values,"enabled",Boolean.class,false))throw new SsoClientFailure(DISABLED);
        var local=identity.find().orElseThrow(()->new SsoClientFailure(INVALID_CONFIGURATION));
        if(!Long.valueOf(1).equals(local.getId()))throw new SsoClientFailure(IDENTITY_MISMATCH);
        try{if(!local.getInstanceId().equals(pin.require(local.getInstanceId())))throw new IllegalStateException();SsoValidation.uuid(local.getInstanceId());SsoValidation.uuid(local.getPortalInstanceId());}
        catch(RuntimeException e){throw new SsoClientFailure(IDENTITY_MISMATCH);}
        var room=classrooms.findByIdentity(local.getPortalInstanceId(),classroom).orElseThrow(()->new SsoClientFailure(IDENTITY_MISMATCH));
        if(!local.getInstanceId().equals(room.getTechnicalAcademyId())||!local.getPortalInstanceId().equals(room.getPortalInstanceId())
            ||!classroom.equals(room.getPortalClassroomId()))throw new SsoClientFailure(IDENTITY_MISMATCH);
        String key=required(values,"active-key-id",String.class);if(!key.equals("S01"))throw invalid();
        String environment=optional(values,"environment",String.class,"PRODUCTION");
        if(!Set.of("PRODUCTION","DEVELOPMENT").contains(environment))throw invalid();
        boolean allow=optional(values,"allow-loopback-http",Boolean.class,false);
        URI base=required(values,"portal-base-url",URI.class);
        validateUrl(base,environment.equals("DEVELOPMENT"),allow);
        int connect=number(values,"connect-timeout-seconds",3,1,30),request=number(values,"request-timeout-seconds",15,1,30);
        return new SsoSettings(local.getInstanceId(),local.getPortalInstanceId(),classroom,base,key,Duration.ofSeconds(connect),Duration.ofSeconds(request));
    }catch(SsoClientFailure e){throw e;}
    catch(ParameterAccessException e){throw new SsoClientFailure(e.getReason()==ParameterAccessException.Reason.STORAGE_FAILURE?REMOTE_UNAVAILABLE:INVALID_CONFIGURATION);}
    catch(RuntimeException e){throw invalid();}}
    public academy.llavor.academia.sso.browser.BrowserSettings loadBrowserEnabled(UUID classroom){
        try{
            var technical=loadEnabled(classroom);
            if(publicHttp!=null&&publicHttp.enabled()){
                return new academy.llavor.academia.sso.browser.BrowserSettings(technical,publicHttp.base("llavor-academia"),publicHttp.base("llavor-portal"));
            }
            var values=parameters.readBatch(List.of(PREFIX+"academy-base-url"));
            return new academy.llavor.academia.sso.browser.BrowserSettings(technical,required(values,"academy-base-url",URI.class));
        }catch(SsoClientFailure e){throw e;}catch(RuntimeException e){throw invalid();}
    }
    static void validateUrl(URI base,boolean development,boolean allowHttp){
        try{
            if(base==null||!ParameterValidation.value(ParameterType.URL,base.toASCIIString()).equals(base.toASCIIString()))throw invalid();
            String path=base.getRawPath();
            if(path.contains("%")||path.contains("\\")||path.contains(";")||path.contains("//")
                ||Arrays.asList(path.split("/",-1)).stream().anyMatch(s->s.equals(".")||s.equals("..")))throw invalid();
            if(!"https".equals(base.getScheme())&&!(development&&allowHttp&&"http".equals(base.getScheme())
                &&Set.of("localhost","127.0.0.1","::1","[::1]").contains(base.getHost())))throw invalid();
        }catch(RuntimeException e){throw invalid();}
    }
    private static <T>T required(Map<String,ParameterReadResult<Object>> values,String suffix,Class<T> type){
        var result=values.get(PREFIX+suffix);
        if(result==null||result.status()!=ParameterReadResult.Status.VALID||!type.isInstance(result.value()))throw invalid();
        return type.cast(result.value());
    }
    private static <T>T optional(Map<String,ParameterReadResult<Object>> values,String suffix,Class<T> type,T fallback){
        var result=values.get(PREFIX+suffix);
        if(result==null||result.status()==ParameterReadResult.Status.MISSING||result.status()==ParameterReadResult.Status.DISABLED)return fallback;
        return required(values,suffix,type);
    }
    private static int number(Map<String,ParameterReadResult<Object>> values,String suffix,long fallback,int min,int max){
        long value=optional(values,suffix,Long.class,fallback);if(value<min||value>max)throw invalid();return (int)value;
    }
    private static SsoClientFailure invalid(){return new SsoClientFailure(INVALID_CONFIGURATION);}
}
