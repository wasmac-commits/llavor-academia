package academy.llavor.academia.sso;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static academy.llavor.academia.sso.SsoClientFailure.Code.*;

class SsoHttpClientTest {
    static final UUID A=SsoConfigurationTest.A,P=SsoConfigurationTest.P,C=SsoConfigurationTest.C;
    static final UUID ACCESS=UUID.fromString("44444444-4444-4444-8444-444444444444");
    static final String SECRET=SsoConfigurationTest.SECRET,TICKET="C".repeat(42)+"A",BINDING="d".repeat(64);
    static final Instant NOW=Instant.parse("2026-01-01T12:00:00Z");
    SsoConfiguration configuration; SsoCredentialProvider credentials; SsoHttpTransport transport;
    SsoSettings settings;SsoHttpClient client;
    @BeforeEach void setup(){
        configuration=mock(SsoConfiguration.class);credentials=mock(SsoCredentialProvider.class);transport=mock(SsoHttpTransport.class);
        settings=new SsoSettings(A,P,C,URI.create("https://portal.example.test/llavor-portal"),"S01",Duration.ofSeconds(3),Duration.ofSeconds(15));
        when(configuration.loadEnabled(C)).thenReturn(settings);
        when(credentials.load(A,"S01")).thenAnswer(i->new SsoCredential(SECRET));
        client=new SsoHttpClient(configuration,credentials,transport,Clock.fixed(NOW,ZoneOffset.UTC));
    }
    JsonObject response(boolean check){
        var b=Json.createObjectBuilder().add("contractVersion",1).add("accessId",ACCESS.toString())
            .add("portalInstanceId",P.toString()).add("userId","1").add("academyId",A.toString())
            .add("classroomId",C.toString()).add("accessExpiresAt",NOW.plusSeconds(3600).toString());
        if(check)b.add("valid",true).add("checkedAt",NOW.toString());
        else b.add("displayName","Synthetic user").add("authorizedAt",NOW.minusSeconds(5).toString());
        return b.build();
    }
    static SsoWireResponse wire(int status,String text){return new SsoWireResponse(status,Map.of("Content-Type",List.of("application/json; charset=UTF-8")),text.getBytes(StandardCharsets.UTF_8));}
    SsoAuthorization prior(){return new SsoAuthorization(ACCESS,P,1,A,C,NOW.minusSeconds(5),NOW.plusSeconds(3600),"Synthetic user");}
    void respond(SsoWireResponse value)throws Exception{when(transport.exchange(any(),any())).thenReturn(value);}
    SsoClientFailure consumeFailure(){return assertThrows(SsoClientFailure.class,()->client.consume(TICKET,BINDING,C));}
    @Test void consumeV1HeadersAndFixedEndpointExactlyOnce()throws Exception{
        respond(wire(200,response(false).toString()));var result=client.consume(TICKET,BINDING,C);
        assertEquals(ACCESS,result.accessId());assertEquals(1,result.userId());assertEquals(P,result.portalId());
        var requests=ArgumentCaptor.forClass(HttpRequest.class);verify(transport,times(1)).exchange(requests.capture(),eq(Duration.ofSeconds(3)));
        HttpRequest request=requests.getValue();assertEquals("POST",request.method());
        assertEquals("https://portal.example.test/llavor-portal"+SsoHttpClient.CONSUME,request.uri().toString());
        assertEquals("Bearer "+SECRET,request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("S01",request.headers().firstValue("X-Llavor-Sso-Key-Id").orElseThrow());
        assertEquals(A.toString(),request.headers().firstValue("X-Llavor-Academy-Id").orElseThrow());
        assertEquals(Duration.ofSeconds(15),request.timeout().orElseThrow());
        // Read synchronously inside exchange in the next test: request bytes are cleared after exchange.
        assertFalse(result.toString().contains(SECRET));assertFalse(result.toString().contains(TICKET));
    }
    @Test void optInLanguageExtensionAndLegacyServerKeepTheSameAuthorization()throws Exception{
        client.i18nEnabled=true;respond(wire(200,Json.createObjectBuilder(response(false)).add("interfaceLanguage","ca-ES-valencia").add("interfaceLanguageVersion",3).build().toString()));
        var result=client.consume(TICKET,BINDING,C);assertEquals("ca-ES-valencia",result.interfaceLanguage());assertEquals(3,result.interfaceLanguageVersion());assertEquals(prior().userId(),result.userId());
        var captured=ArgumentCaptor.forClass(HttpRequest.class);verify(transport).exchange(captured.capture(),any());assertEquals("1",captured.getValue().headers().firstValue("X-Llavor-I18n").orElseThrow());
        respond(wire(200,response(false).toString()));assertEquals("es",client.consume(TICKET,BINDING,C).interfaceLanguage());
    }
    @Test void forgedOrPartialLanguageMetadataIsRejected()throws Exception{
        for(var value:List.of(Json.createObjectBuilder(response(false)).add("interfaceLanguage","ca-ES-valencia").build(),
            Json.createObjectBuilder(response(false)).add("interfaceLanguage","ca_ES_valencia").add("interfaceLanguageVersion",0).build(),
            Json.createObjectBuilder(response(false)).add("interfaceLanguage","es").add("interfaceLanguageVersion",-2).build())){
            respond(wire(200,value.toString()));assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
        }
    }
    @Test void outgoingContractAndRequestLimit()throws Exception{
        when(transport.exchange(any(),any())).thenAnswer(i->{
            var request=(HttpRequest)i.getArgument(0);byte[] body=body(request);
            assertTrue(body.length<=4096);
            try(var reader=Json.createReader(new ByteArrayInputStream(body))){
                var json=reader.readObject();assertEquals(Set.of("contractVersion","ticket","academyId","classroomId","browserBindingHash"),json.keySet());
                assertEquals(1,json.getInt("contractVersion"));assertEquals(TICKET,json.getString("ticket"));assertEquals(BINDING,json.getString("browserBindingHash"));
            }
            return wire(200,response(false).toString());
        });client.consume(TICKET,BINDING,C);
    }
    @Test void checkRevalidatesEveryTimeAndKeepsDeadline()throws Exception{
        when(transport.exchange(any(),any())).thenAnswer(i->{
            var r=(HttpRequest)i.getArgument(0);assertTrue(r.uri().toString().endsWith(SsoHttpClient.CHECK));
            try(var reader=Json.createReader(new ByteArrayInputStream(body(r)))){
                var json=reader.readObject();assertEquals(Set.of("contractVersion","accessId","academyId","classroomId"),json.keySet());
                assertEquals(ACCESS.toString(),json.getString("accessId"));assertFalse(json.containsKey("ticket"));
            }
            return wire(200,response(true).toString());
        });
        assertEquals(prior(),client.revalidate(prior()).authorization());client.revalidate(prior());
        verify(transport,times(2)).exchange(any(),any());verify(credentials,times(2)).load(A,"S01");
    }
    @Test void logoutUsesAuthenticatedFixedEndpointAndRequiresExplicitConfirmation()throws Exception{
        when(transport.exchange(any(),any())).thenAnswer(i->{
            HttpRequest request=i.getArgument(0);assertEquals("POST",request.method());
            assertEquals(settings.portalBaseUrl()+SsoHttpClient.LOGOUT,request.uri().toASCIIString());
            assertEquals("Bearer "+SECRET,request.headers().firstValue("Authorization").orElseThrow());
            try(var reader=Json.createReader(new ByteArrayInputStream(body(request)))){
                var json=reader.readObject();assertEquals(Set.of("contractVersion","accessId","academyId","classroomId"),json.keySet());
                assertEquals(ACCESS.toString(),json.getString("accessId"));
            }
            return wire(200,"{\"contractVersion\":1,\"revoked\":true}");
        });
        assertEquals(settings.portalBaseUrl()+"/login.xhtml",client.logout(prior()).toASCIIString());
        doReturn(wire(200,"{\"contractVersion\":1,\"revoked\":false}")).when(transport).exchange(any(),any());
        assertThrows(SsoClientFailure.class,()->client.logout(prior()));
        doReturn(wire(302,"{}")).when(transport).exchange(any(),any());assertThrows(SsoClientFailure.class,()->client.logout(prior()));
    }
    static Stream<Arguments> errors(){return Stream.of(
        Arguments.of(400,"INVALID_REQUEST",INVALID_REQUEST,false),Arguments.of(401,"AUTHENTICATION_FAILED",AUTHENTICATION_REJECTED,false),
        Arguments.of(403,"FORBIDDEN",ACCESS_DENIED,false),Arguments.of(403,"ACCESS_UNAVAILABLE",ACCESS_DENIED,false),
        Arguments.of(410,"TICKET_UNAVAILABLE",TICKET_UNAVAILABLE,false),Arguments.of(503,"SERVICE_UNAVAILABLE",REMOTE_UNAVAILABLE,true));}
    @ParameterizedTest @MethodSource("errors")
    void controlledErrors(int status,String code,SsoClientFailure.Code expected,boolean uncertain)throws Exception{
        respond(wire(status,"{\"contractVersion\":1,\"error\":{\"code\":\""+code+"\"}}"));
        var e=consumeFailure();assertEquals(expected,e.getCode());assertEquals(uncertain,e.isConsumptionMayHaveCommitted());
        verify(transport,times(1)).exchange(any(),any());assertNull(e.getCause());
    }
    static Stream<Arguments> transportErrors(){return Stream.of(
        Arguments.of(new HttpTimeoutException("sensitive payload"),TIMEOUT),
        Arguments.of(new SSLHandshakeException("sensitive payload"),TLS_REJECTED),
        Arguments.of(new IOException("sensitive payload"),REMOTE_UNAVAILABLE));}
    @ParameterizedTest @MethodSource("transportErrors")
    void uncertainConsumeNeverRetries(Exception failure,SsoClientFailure.Code code)throws Exception{
        when(transport.exchange(any(),any())).thenThrow(failure);
        var e=consumeFailure();assertEquals(code,e.getCode());assertTrue(e.isConsumptionMayHaveCommitted());
        assertFalse(e.toString().contains("sensitive"));assertFalse(e.toString().contains(SECRET));assertNull(e.getCause());
        verify(transport,times(1)).exchange(any(),any());
    }
    @Test void interruptedRestoresFlagAndDoesNotRetry()throws Exception{
        when(transport.exchange(any(),any())).thenThrow(new InterruptedException("sensitive"));
        try{assertTrue(consumeFailure().isConsumptionMayHaveCommitted());assertTrue(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
        verify(transport,times(1)).exchange(any(),any());
    }
    @Test void revalidationNeverGrantsOffline()throws Exception{
        when(transport.exchange(any(),any())).thenThrow(new IOException("unavailable"));
        var e=assertThrows(SsoClientFailure.class,()->client.revalidate(prior()));
        assertEquals(REMOTE_UNAVAILABLE,e.getCode());assertFalse(e.isConsumptionMayHaveCommitted());
    }
    @ParameterizedTest @ValueSource(ints={301,302,307,308})
    void redirectsRejected(int status)throws Exception{respond(wire(status,"ignored"));assertEquals(REDIRECT_REJECTED,consumeFailure().getCode());verify(transport,times(1)).exchange(any(),any());}
    @Test void disabledBeforeAliasOrTransport(){
        when(configuration.loadEnabled(C)).thenThrow(new SsoClientFailure(DISABLED));assertEquals(DISABLED,consumeFailure().getCode());verifyNoInteractions(credentials,transport);
    }
    @Test void unsafeDiagnosticsPreventAliasRead(){
        doThrow(new SsoClientFailure(INVALID_CONFIGURATION)).when(transport).validateEnvironment();
        assertEquals(INVALID_CONFIGURATION,consumeFailure().getCode());verifyNoInteractions(credentials);
    }
    @Test void missingAliasBeforeAnyExchange()throws Exception{
        when(credentials.load(A,"S01")).thenThrow(new SsoClientFailure(CREDENTIAL_UNAVAILABLE));
        assertEquals(CREDENTIAL_UNAVAILABLE,consumeFailure().getCode());verify(transport,never()).exchange(any(),any());
    }
    @Test void malformedTicketOrBindingBeforeAnyDependency(){
        assertThrows(SsoClientFailure.class,()->client.consume("bad",BINDING,C));
        assertThrows(SsoClientFailure.class,()->client.consume(TICKET,"bad",C));verifyNoInteractions(configuration,credentials,transport);
    }
    @Test void expiredOrContradictoryPriorDeniedBeforeRequest()throws Exception{
        var expired=new SsoAuthorization(ACCESS,P,1,A,C,NOW.minusSeconds(3600),NOW,"Synthetic");
        assertEquals(ACCESS_DENIED,assertThrows(SsoClientFailure.class,()->client.revalidate(expired)).getCode());
        var other=new SsoAuthorization(ACCESS,C,1,A,C,NOW,NOW.plusSeconds(60),"Synthetic");
        assertEquals(IDENTITY_MISMATCH,assertThrows(SsoClientFailure.class,()->client.revalidate(other)).getCode());
        verify(transport,never()).exchange(any(),any());verifyNoInteractions(credentials);
    }
    @ParameterizedTest @ValueSource(strings={"contractVersion","accessId","portalInstanceId","userId","academyId","classroomId","accessExpiresAt","displayName","authorizedAt"})
    void incompleteResponseRejected(String field)throws Exception{
        var altered=Json.createObjectBuilder(response(false)).remove(field).build();
        respond(wire(200,altered.toString()));assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
    }
    static Stream<Arguments> contradictions(){return Stream.of(
        Arguments.of("contractVersion","2"),Arguments.of("contractVersion","1.0"),Arguments.of("userId","1"),
        Arguments.of("userId","\"0\""),Arguments.of("academyId","\""+P+"\""),
        Arguments.of("portalInstanceId","\""+A+"\""),Arguments.of("classroomId","\""+A+"\""),
        Arguments.of("accessId","\"00000000-0000-0000-0000-000000000000\""),
        Arguments.of("authorizedAt","\"2026-01-01T12:01:00Z\""),Arguments.of("accessExpiresAt","\"2026-01-01T11:00:00Z\""),
        Arguments.of("accessExpiresAt","\"2026-01-02T12:00:00Z\""),Arguments.of("accessExpiresAt","\"2026-01-01T13:00:00+00:00\""));}
    @ParameterizedTest @MethodSource("contradictions")
    void contradictoryValuesRejected(String field,String replacement)throws Exception{
        var original=response(false);var b=Json.createObjectBuilder(original);
        try(var reader=Json.createReader(new StringReader("{\"x\":"+replacement+"}"))){b.add(field,reader.readObject().get("x"));}
        respond(wire(200,b.build().toString()));assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
    }
    @ParameterizedTest @ValueSource(strings={"<html>login</html>","{}","{\"contractVersion\":1}","[]","null"})
    void invalidOrHtmlBodyRejected(String text)throws Exception{
        respond(wire(200,text));assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
    }
    @Test void duplicatesUnknownTrailingDocumentsAndMalformedUtf8Rejected()throws Exception{
        String good=response(false).toString();
        for(String bad:List.of(good.replace("\"contractVersion\":1","\"contractVersion\":1,\"contractVersion\":1"),
                good.substring(0,good.length()-1)+",\"unexpected\":true}",good+"{}", "\ufeff"+good)){
            respond(wire(200,bad));assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
        }
        respond(new SsoWireResponse(200,Map.of("Content-Type",List.of("application/json")),new byte[]{(byte)0xc3,(byte)0x28}));
        assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
    }
    @Test void wrongMediaTypeAnd404AreNeverProof()throws Exception{
        respond(new SsoWireResponse(200,Map.of("Content-Type",List.of("text/html")),response(false).toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
        respond(wire(404,"{\"contractVersion\":1,\"error\":{\"code\":\"NOT_FOUND\"}}"));assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
        respond(new SsoWireResponse(200,Map.of("Content-Type",List.of("application/json","text/html")),response(false).toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(INVALID_RESPONSE,consumeFailure().getCode());
    }
    @Test void checkRejectsFalseChangedUserAndExtendedDeadline()throws Exception{
        for(var bad:List.of(Json.createObjectBuilder(response(true)).add("valid",false).build(),
                Json.createObjectBuilder(response(true)).add("userId","2").build(),
                Json.createObjectBuilder(response(true)).add("accessExpiresAt",NOW.plusSeconds(3601).toString()).build(),
                Json.createObjectBuilder(response(true)).add("checkedAt",NOW.minusSeconds(60).toString()).build())){
            respond(wire(200,bad.toString()));assertEquals(INVALID_RESPONSE,assertThrows(SsoClientFailure.class,()->client.revalidate(prior())).getCode());
        }
    }
    @Test void wireBoundAndNoSensitiveToString(){
        assertThrows(SsoClientFailure.class,()->new SsoWireResponse(200,Map.of(),new byte[4097]));
        assertFalse(wire(401,SECRET).toString().contains(SECRET));
    }
    static byte[] body(HttpRequest request){
        var bytes=new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer b){while(b.hasRemaining())bytes.write(b.get());}
            public void onError(Throwable e){throw new AssertionError(e);}
            public void onComplete(){}
        });
        return bytes.toByteArray();
    }
}
