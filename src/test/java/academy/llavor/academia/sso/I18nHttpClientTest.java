package academy.llavor.academia.sso;
import academy.llavor.academia.i18n.core.*;
import academy.llavor.academia.i18n.service.LocalTranslationService;
import jakarta.json.Json;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class I18nHttpClientTest {
    I18nHttpClient client;HttpClient http;HttpResponse<byte[]> response;SsoAuthorization grant;HttpRequest sent;byte[] sentBody;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        client=new I18nHttpClient();client.configuration=mock(SsoConfiguration.class);client.credentials=mock(SsoCredentialProvider.class);client.transport=mock(JdkSsoHttpTransport.class);client.local=mock(LocalTranslationService.class);http=mock(HttpClient.class);response=mock(HttpResponse.class);
        var p=UUID.fromString("22222222-2222-4222-8222-222222222222");var a=UUID.fromString("11111111-1111-4111-8111-111111111111");var c=UUID.fromString("33333333-3333-4333-8333-333333333333");
        grant=new SsoAuthorization(UUID.randomUUID(),p,7,a,c,Instant.now(),Instant.now().plusSeconds(600),"Synthetic");
        when(client.configuration.loadEnabled(c)).thenReturn(new SsoSettings(a,p,c,URI.create("https://portal.example.test/llavor-portal"),"S01",Duration.ofSeconds(2),Duration.ofSeconds(3)));
        when(client.credentials.load(a,"S01")).thenAnswer(i->new SsoCredential(SsoConfigurationTest.SECRET));when(client.transport.client(any())).thenReturn(http);
        when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(i->{sent=i.getArgument(0);sentBody=SsoHttpClientTest.body(sent);return CompletableFuture.completedFuture(response);});
        reply(200,"{}","");
    }
    void reply(int status,String payload,String digest){when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));var headers=new HashMap<String,List<String>>();headers.put("Content-Type",List.of("application/json; charset=UTF-8"));if(!digest.isEmpty())headers.put("X-Llavor-Catalog-Sha256",List.of(digest));when(response.headers()).thenReturn(HttpHeaders.of(headers,(k,v)->true));}
    CatalogSnapshot catalog(long version,UUID portal){return new CatalogSnapshot(1,portal,"ui",version,version==0?Instant.EPOCH:Instant.parse("2026-11-23T08:00:00Z"),Map.of("es","Spanish"),Map.of("es",Map.of("common.greeting","Hola")));}
    @Test void disabledByDefaultNeverLoadsSecretsOrContactsPortal(){assertThrows(SsoClientFailure.class,()->client.refresh(grant));verifyNoInteractions(client.configuration,client.credentials,client.transport,client.local,http);}
    @Test void applicationFlagChangesAreObservedWithoutRecreatingClient(){var value=new java.util.concurrent.atomic.AtomicBoolean(false);client.enabledValue=value::get;assertFalse(client.enabled());value.set(true);assertTrue(client.enabled());value.set(false);assertFalse(client.enabled());}
    @Test void validPublishedCatalogUsesFixedAuthenticatedRouteThenAtomicLocalInstaller(){client.enabled=true;String payload=CatalogCodec.encode(catalog(2,grant.portalId()));reply(200,payload,CatalogCodec.digest(payload));client.refresh(grant);
        assertEquals("https://portal.example.test/llavor-portal/internal/v1/sso/i18n/catalog",sent.uri().toString());assertEquals("POST",sent.method());assertTrue(sent.headers().firstValue("Authorization").orElseThrow().startsWith("Bearer "));assertTrue(sent.timeout().isPresent());verify(client.local).install(payload,CatalogCodec.digest(payload));
    }
    @Test void corruptForeignBootstrapRedirectAndMissingDigestNeverReachLocalInstall(){client.enabled=true;
        for(var snapshot:List.of(catalog(1,UUID.randomUUID()),catalog(0,grant.portalId()))){String payload=CatalogCodec.encode(snapshot);reply(200,payload,CatalogCodec.digest(payload));assertThrows(RuntimeException.class,()->client.refresh(grant));}
        String payload=CatalogCodec.encode(catalog(1,grant.portalId()));reply(200,payload,"0".repeat(64));assertThrows(RuntimeException.class,()->client.refresh(grant));reply(200,payload,"");assertThrows(RuntimeException.class,()->client.refresh(grant));reply(302,payload,CatalogCodec.digest(payload));assertThrows(RuntimeException.class,()->client.refresh(grant));verifyNoInteractions(client.local);
    }
    @Test void preferenceWriteCannotContainBrowserUserIdAndPreservesExpectedVersion(){client.enabled=true;reply(200,"{\"contractVersion\":1,\"language\":\"ca-ES-valencia\",\"preferenceVersion\":4}","");var preference=client.save(grant,"CA-es-VALENCIA",3);assertEquals("ca-ES-valencia",preference.language());assertEquals(4,preference.version());
        try(var reader=Json.createReader(new java.io.ByteArrayInputStream(sentBody))){var json=reader.readObject();assertFalse(json.containsKey("userId"));assertEquals(grant.accessId().toString(),json.getString("accessId"));assertEquals("3",json.getString("expectedVersion"));assertEquals("ca-ES-valencia",json.getString("language"));}
    }
    @Test void unavailablePortalLeavesPersistentLocalCatalogUntouched(){client.enabled=true;reply(503,"{}","");assertThrows(SsoClientFailure.class,()->client.refresh(grant));verifyNoInteractions(client.local);}
    @Test void preferenceUsesExactAuthenticatedPostAndAcceptsOnlyConfirmedSuccess(){
        client.enabled=true;reply(200,"{\"contractVersion\":1,\"language\":\"es\",\"preferenceVersion\":5}","");
        assertEquals(new I18nHttpClient.Preference("es",5),client.save(grant,"es",4));
        assertEquals("POST",sent.method());assertEquals("/llavor-portal/internal/v1/sso/i18n/preference",sent.uri().getPath());
        assertEquals("S01",sent.headers().firstValue("X-Llavor-Sso-Key-Id").orElseThrow());
        assertEquals(grant.academyId().toString(),sent.headers().firstValue("X-Llavor-Academy-Id").orElseThrow());
        assertEquals("Bearer "+SsoConfigurationTest.SECRET,sent.headers().firstValue("Authorization").orElseThrow());
        for(int status:List.of(400,401,403,409,500,503,302)){
            reply(status,"{}","");assertThrows(SsoClientFailure.class,()->client.save(grant,"es",4),"HTTP "+status);
        }
        reply(200,"{\"contractVersion\":1,\"language\":\"es\"}","");assertThrows(SsoClientFailure.class,()->client.save(grant,"es",4));
        verifyNoInteractions(client.local);
    }
    @Test void timedOutPreferenceCanBeRetriedExplicitlyWithoutAutomaticReplay(){
        client.enabled=true;var pending=new CompletableFuture<HttpResponse<byte[]>>();pending.completeExceptionally(new HttpTimeoutException("Synthetic timeout"));
        when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(pending);
        assertThrows(SsoClientFailure.class,()->client.save(grant,"es",4));verify(http,times(1)).sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        reply(200,"{\"contractVersion\":1,\"language\":\"es\",\"preferenceVersion\":5}","");
        when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(CompletableFuture.completedFuture(response));
        assertEquals(5,client.save(grant,"es",4).version());
    }
    @Test void boundedCatalogTransportAllowsMoreThanSsoButRejectsOversizedChunks(){
        var body=new JdkSsoHttpTransport.LimitedBody(CatalogCodec.MAX_BYTES);var subscription=mock(Flow.Subscription.class);body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[4097])));body.onComplete();assertEquals(4097,body.getBody().toCompletableFuture().join().length);
        var limited=new JdkSsoHttpTransport.LimitedBody(64);var overflow=mock(Flow.Subscription.class);limited.onSubscribe(overflow);limited.onNext(List.of(ByteBuffer.wrap(new byte[65])));assertThrows(CompletionException.class,()->limited.getBody().toCompletableFuture().join());verify(overflow).cancel();
    }
}
