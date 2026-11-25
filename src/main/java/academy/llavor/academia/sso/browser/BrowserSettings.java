package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import academy.llavor.academia.parameter.entity.ParameterType;
import java.net.URI;
import java.util.Set;
public record BrowserSettings(SsoSettings technical,URI academyBaseUrl,URI portalPublicUrl) {
    public BrowserSettings(SsoSettings technical,URI academyBaseUrl){this(technical,academyBaseUrl,technical==null?null:technical.portalBaseUrl());}
    public BrowserSettings {
        try{
            if(technical==null||portalPublicUrl==null||academyBaseUrl==null||!ParameterValidation.value(ParameterType.URL,academyBaseUrl.toASCIIString()).equals(academyBaseUrl.toASCIIString()))throw new IllegalArgumentException();
            String path=academyBaseUrl.getRawPath();
            if(path.contains("%")||path.contains("\\")||path.contains(";")||path.contains("//")
                    ||java.util.Arrays.stream(path.split("/")).anyMatch(x->x.equals(".")||x.equals("..")))throw new IllegalArgumentException();
            String scheme=portalPublicUrl.getScheme();
            if(!portalPublicUrl.equals(technical.portalBaseUrl())&&!("https".equals(scheme)&&portalPublicUrl.getHost()!=null&&portalPublicUrl.getRawPath().matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")&&portalPublicUrl.getRawUserInfo()==null&&portalPublicUrl.getRawQuery()==null&&portalPublicUrl.getRawFragment()==null))throw new IllegalArgumentException();
            if(!scheme.equals(academyBaseUrl.getScheme())||!Set.of("http","https").contains(scheme))throw new IllegalArgumentException();
            // Technical configuration already proves DEVELOPMENT + explicit permission for HTTP.
            if(scheme.equals("http")&&(!Set.of("localhost","127.0.0.1","[::1]","::1").contains(academyBaseUrl.getHost())
                    ||!origin(academyBaseUrl).equals(origin(technical.portalBaseUrl()))))throw new IllegalArgumentException();
        }catch(RuntimeException e){throw new SsoClientFailure(SsoClientFailure.Code.INVALID_CONFIGURATION);}
    }
    public static String origin(URI uri){
        int port=uri.getPort();return uri.getScheme()+"://"+uri.getHost()+((port<0||port==80&&uri.getScheme().equals("http")||port==443&&uri.getScheme().equals("https"))?"":":"+port);
    }
    public String portalOrigin(){return origin(portalPublicUrl);}
    public String academyOrigin(){return origin(academyBaseUrl);}
}
