package academy.llavor.academia.sso;
import academy.llavor.academia.i18n.core.*;
import academy.llavor.academia.i18n.service.LocalTranslationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
/** Explicit actions only. Reuses configured destination, S01 decryption boundary and native TLS policy. */
@ApplicationScoped @Transactional(Transactional.TxType.NOT_SUPPORTED)
public class I18nHttpClient {
    @Inject @ConfigProperty(name="llavor.i18n.exchange-enabled",defaultValue="false") jakarta.inject.Provider<Boolean> enabledValue;
    boolean enabled;
    @Inject SsoConfiguration configuration;
    @Inject SsoCredentialProvider credentials;
    @Inject JdkSsoHttpTransport transport;
    @Inject LocalTranslationService local;
    public record Preference(String language,long version){}
    public boolean enabled(){return enabledValue==null?enabled:Boolean.TRUE.equals(enabledValue.get());}
    public boolean refresh(SsoAuthorization grant){
        var result=exchange(grant,"/catalog",null,null,CatalogCodec.MAX_BYTES);
        // SHA-256 checks corruption; authentication is the bounded authenticated HTTP exchange above.
        return local.install(result.body(),result.digest());
    }
    public Preference read(SsoAuthorization grant){return preference(exchange(grant,"/preference/read",null,null,SsoWireResponse.LIMIT).body());}
    public Preference save(SsoAuthorization grant,String language,long expected){
        return preference(exchange(grant,"/preference",LanguageTags.normalize(language),expected,SsoWireResponse.LIMIT).body());
    }
    private static Preference preference(String text){
        var values=SsoResponseReader.object(text);
        if(!values.keySet().equals(Set.of("contractVersion","language","preferenceVersion"))||!(values.get("contractVersion") instanceof JsonNumber contract)||!contract.toString().equals("1")
            ||!(values.get("language") instanceof JsonString language)||!(values.get("preferenceVersion") instanceof JsonNumber version)||!version.isIntegral())throw invalid();
        String tag=LanguageTags.normalize(language.getString());long row=version.longValueExact();if(!tag.equals(language.getString())||row< -1)throw invalid();return new Preference(tag,row);
    }
    private record Result(String body,String digest){}
    private Result exchange(SsoAuthorization grant,String suffix,String language,Long expected,int limit){
        if(!enabled())throw new SsoClientFailure(SsoClientFailure.Code.DISABLED);
        if(grant==null)throw invalid();var settings=configuration.loadEnabled(grant.classroomId());
        if(!settings.portalId().equals(grant.portalId())||!settings.academyId().equals(grant.academyId()))throw invalid();
        var body=Json.createObjectBuilder().add("contractVersion",1).add("academyId",settings.academyId().toString()).add("classroomId",settings.classroomId().toString()).add("accessId",grant.accessId().toString());
        if(language!=null)body.add("language",language).add("expectedVersion",Long.toString(expected));
        byte[] bytes=body.build().toString().getBytes(StandardCharsets.UTF_8);
        transport.validateEnvironment();
        try(var credential=credentials.load(settings.academyId(),settings.keyId())){
            return credential.use(secret->{
                var request=HttpRequest.newBuilder(URI.create(settings.portalBaseUrl().toASCIIString()+"/internal/v1/sso/i18n"+suffix)).timeout(settings.requestTimeout())
                    .header("Content-Type","application/json; charset=UTF-8").header("Accept","application/json").header("Authorization","Bearer "+secret)
                    .header("X-Llavor-Sso-Key-Id",settings.keyId()).header("X-Llavor-Academy-Id",settings.academyId().toString()).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
                try(var http=transport.client(settings.connectTimeout())){
                    var pending=http.sendAsync(request,info->new JdkSsoHttpTransport.LimitedBody(limit));
                    try{
                        var response=pending.get(settings.requestTimeout().toMillis(),TimeUnit.MILLISECONDS);
                        if(response.statusCode()!=200)throw new SsoClientFailure(response.statusCode()==409?SsoClientFailure.Code.INVALID_REQUEST:SsoClientFailure.Code.REMOTE_UNAVAILABLE);
                        if(!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;\\s*charset=utf-8)?")
                            ||response.headers().allValues("Content-Type").size()!=1
                            ||!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity"))throw invalid();
                        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();
                        if(text.isEmpty()||text.charAt(0)=='\ufeff')throw invalid();
                        var digests=response.headers().allValues("X-Llavor-Catalog-Sha256");String digest=digests.size()==1?digests.getFirst():"";
                        // Validate the catalog before handing it to the atomic persistence service.
                        if(suffix.equals("/catalog")){var catalog=CatalogCodec.decode(text,digest);if(catalog.version()<1||!catalog.portalInstanceId().equals(settings.portalId())||!catalog.catalogCode().equals("ui"))throw invalid();}
                        return new Result(text,digest);
                    }catch(InterruptedException interrupted){pending.cancel(true);Thread.currentThread().interrupt();throw new SsoClientFailure(SsoClientFailure.Code.REMOTE_UNAVAILABLE);}
                    catch(TimeoutException timeout){pending.cancel(true);throw new SsoClientFailure(SsoClientFailure.Code.TIMEOUT);}
                    catch(ExecutionException|CharacterCodingException failure){pending.cancel(true);throw new SsoClientFailure(SsoClientFailure.Code.REMOTE_UNAVAILABLE);}
                }
            });
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    private static SsoClientFailure invalid(){return new SsoClientFailure(SsoClientFailure.Code.INVALID_RESPONSE);}
}
