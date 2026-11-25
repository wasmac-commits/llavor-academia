package academy.llavor.academia.sso;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdkSsoHttpTransportTest {
    @Test void actualClientHasStrictTlsAndNoRedirectOrCredentialHandlers(){
        var client=new JdkSsoHttpTransport().client(Duration.ofSeconds(3));
        try(client){
            assertEquals(HttpClient.Redirect.NEVER,client.followRedirects());
            assertEquals("HTTPS",client.sslParameters().getEndpointIdentificationAlgorithm());
            assertArrayEquals(new String[]{"TLSv1.3","TLSv1.2"},client.sslParameters().getProtocols());
            assertEquals(List.of(Proxy.NO_PROXY),client.proxy().orElseThrow().select(URI.create("https://portal.test")));
            assertTrue(client.authenticator().isEmpty());assertTrue(client.cookieHandler().isEmpty());
            assertEquals(Duration.ofSeconds(3),client.connectTimeout().orElseThrow());
        }
    }
    @ParameterizedTest @ValueSource(strings={"javax.net.debug","jdk.httpclient.HttpClient.log","jdk.internal.httpclient.disableHostnameVerification","jdk.httpclient.enableAllMethodRetry"})
    void unsafeGlobalOverridesAreRejectedWithoutChangingThem(String key){
        String old=System.getProperty(key);
        try{
            System.setProperty(key,key.endsWith(".log")?"headers":"true");
            assertThrows(SsoClientFailure.class,()->new JdkSsoHttpTransport().validateEnvironment());
        }finally{if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
    }
    @Test @SuppressWarnings("unchecked")
    void nativeSendIsCalledOnceAndEntireResponseHasDeadline() throws Exception {
        var http=mock(HttpClient.class);
        var transport=spy(new JdkSsoHttpTransport());
        doReturn(http).when(transport).client(Duration.ofSeconds(1));
        CompletableFuture<java.net.http.HttpResponse<byte[]>> pending=new CompletableFuture<>();
        doReturn(pending).when(http).sendAsync(any(java.net.http.HttpRequest.class),any(java.net.http.HttpResponse.BodyHandler.class));
        var request=java.net.http.HttpRequest.newBuilder(URI.create("https://portal.test"))
            .timeout(Duration.ofMillis(5)).POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThrows(java.net.http.HttpTimeoutException.class,()->transport.exchange(request,Duration.ofSeconds(1)));
        assertTrue(pending.isCancelled());
        verify(http,times(1)).sendAsync(any(java.net.http.HttpRequest.class),any(java.net.http.HttpResponse.BodyHandler.class));
        verify(http).close();
    }
    @Test void missingRequestDeadlineOrWrongMethodIsRejectedWithoutSocket(){
        var transport=spy(new JdkSsoHttpTransport());
        var request=java.net.http.HttpRequest.newBuilder(URI.create("https://portal.test")).GET().build();
        assertThrows(SsoClientFailure.class,()->transport.exchange(request,Duration.ofSeconds(1)));
        verify(transport,never()).client(any());
    }
    @Test void chunkedBodyActualBytesAreBounded(){
        var body=new JdkSsoHttpTransport.LimitedBody();var subscription=mock(Flow.Subscription.class);
        body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[4096])));
        body.onNext(List.of(ByteBuffer.wrap(new byte[1])));
        verify(subscription).cancel();
        assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());
    }
    @Test void boundaryAndTransportFailuresNeverLeakOriginalException(){
        var body=new JdkSsoHttpTransport.LimitedBody();body.onSubscribe(mock(Flow.Subscription.class));
        body.onNext(List.of(ByteBuffer.wrap(new byte[4096])));body.onComplete();
        assertEquals(4096,body.getBody().toCompletableFuture().join().length);
        var failed=new JdkSsoHttpTransport.LimitedBody();failed.onError(new RuntimeException("synthetic secret"));
        var e=assertThrows(CompletionException.class,()->failed.getBody().toCompletableFuture().join());
        assertFalse(e.toString().contains("synthetic"));assertNull(e.getCause().getCause());
    }
}
