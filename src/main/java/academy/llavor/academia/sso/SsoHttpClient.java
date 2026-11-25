package academy.llavor.academia.sso;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static academy.llavor.academia.sso.SsoClientFailure.Code.*;

/** Technical S01 exchanges only. No browser endpoint, session, grant cache or retry. */
@ApplicationScoped @Transactional(Transactional.TxType.NOT_SUPPORTED)
public class SsoHttpClient {
    public static final String CONSUME="/internal/v1/sso/tickets/consume";
    public static final String LOGOUT="/internal/v1/sso/access/logout";
    public static final String CHECK="/internal/v1/sso/access/check";
    private SsoConfiguration configuration; private SsoCredentialProvider credentials; private SsoHttpTransport transport;
    private Clock clock; private final SsoResponseReader responses=new SsoResponseReader();
    @Inject @org.eclipse.microprofile.config.inject.ConfigProperty(name="llavor.i18n.exchange-enabled",defaultValue="false") jakarta.inject.Provider<Boolean> i18nEnabledValue;
    boolean i18nEnabled;
    protected SsoHttpClient(){}
    @Inject public SsoHttpClient(SsoConfiguration configuration,SsoCredentialProvider credentials,SsoHttpTransport transport){
        this(configuration,credentials,transport,Clock.systemUTC());
    }
    SsoHttpClient(SsoConfiguration configuration,SsoCredentialProvider credentials,SsoHttpTransport transport,Clock clock){
        this.configuration=configuration;this.credentials=credentials;this.transport=transport;this.clock=clock;
    }
    public SsoAuthorization consume(String ticket,String browserBindingHash,UUID classroomId){
        SsoValidation.canonical32(ticket);SsoValidation.binding(browserBindingHash);
        SsoSettings settings=configuration.loadEnabled(classroomId);
        JsonObject body=Json.createObjectBuilder().add("contractVersion",1).add("ticket",ticket)
                .add("academyId",settings.academyId().toString()).add("classroomId",settings.classroomId().toString())
                .add("browserBindingHash",browserBindingHash).build();
        return responses.consumed(exchange(settings,CONSUME,body,true),settings,clock.instant());
    }
    public SsoRevalidation revalidate(SsoAuthorization authorization){
        try{
            if(authorization==null || authorization.userId()<1)throw new IllegalArgumentException();
            SsoValidation.uuid(authorization.accessId());SsoValidation.uuid(authorization.portalId());
            SsoValidation.uuid(authorization.academyId());SsoValidation.uuid(authorization.classroomId());
            if(authorization.accessExpiresAt()==null || !authorization.accessExpiresAt().isAfter(clock.instant()))
                throw new SsoClientFailure(ACCESS_DENIED);
        }catch(SsoClientFailure e){throw e;}
        catch(RuntimeException e){throw new SsoClientFailure(INVALID_REQUEST);}
        SsoSettings settings=configuration.loadEnabled(authorization.classroomId());
        if(!authorization.portalId().equals(settings.portalId()) || !authorization.academyId().equals(settings.academyId()))
            throw new SsoClientFailure(IDENTITY_MISMATCH);
        JsonObject body=Json.createObjectBuilder().add("contractVersion",1).add("accessId",authorization.accessId().toString())
                .add("academyId",settings.academyId().toString()).add("classroomId",settings.classroomId().toString()).build();
        return responses.checked(exchange(settings,CHECK,body,false),settings,authorization,clock.instant());
    }
    /** No authorization cache or browser supplied destination. Logout also accepts expired grants. */
    public URI logout(SsoAuthorization authorization){
        if(authorization==null)throw new SsoClientFailure(INVALID_REQUEST);
        SsoValidation.uuid(authorization.accessId());
        SsoSettings settings=configuration.loadEnabled(authorization.classroomId());
        if(!authorization.portalId().equals(settings.portalId())||!authorization.academyId().equals(settings.academyId()))
            throw new SsoClientFailure(IDENTITY_MISMATCH);
        JsonObject body=Json.createObjectBuilder().add("contractVersion",1).add("accessId",authorization.accessId().toString())
            .add("academyId",settings.academyId().toString()).add("classroomId",settings.classroomId().toString()).build();
        responses.loggedOut(exchange(settings,LOGOUT,body,false));
        return URI.create(settings.portalBaseUrl().toASCIIString()+"/login.xhtml");
    }
    private SsoWireResponse exchange(SsoSettings settings,String suffix,JsonObject body,boolean consumption){
        transport.validateEnvironment();
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>SsoWireResponse.LIMIT)throw new SsoClientFailure(INVALID_REQUEST);
        try(SsoCredential credential=credentials.load(settings.academyId(),settings.keyId())){
            return credential.use(secret->{
                var builder=HttpRequest.newBuilder(URI.create(settings.portalBaseUrl().toASCIIString()+suffix))
                        .timeout(settings.requestTimeout()).header("Content-Type","application/json; charset=UTF-8")
                        .header("Accept","application/json").header("Authorization","Bearer "+secret)
                        .header("X-Llavor-Sso-Key-Id",settings.keyId())
                        .header("X-Llavor-Academy-Id",settings.academyId().toString())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
                if(consumption&&(i18nEnabledValue==null?i18nEnabled:Boolean.TRUE.equals(i18nEnabledValue.get())))builder.header("X-Llavor-I18n","1");
                HttpRequest request=builder.build();
                try{return transport.exchange(request,settings.connectTimeout());}
                catch(HttpTimeoutException e){throw new SsoClientFailure(TIMEOUT,consumption);}
                catch(SSLException e){throw new SsoClientFailure(TLS_REJECTED,consumption);}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new SsoClientFailure(REMOTE_UNAVAILABLE,consumption);}
                catch(IOException e){throw new SsoClientFailure(REMOTE_UNAVAILABLE,consumption);}
                catch(SsoClientFailure e){throw new SsoClientFailure(e.getCode(),consumption);}
                catch(RuntimeException e){throw new SsoClientFailure(REMOTE_UNAVAILABLE,consumption);}
            });
        }finally{Arrays.fill(bytes,(byte)0);}
    }
}
