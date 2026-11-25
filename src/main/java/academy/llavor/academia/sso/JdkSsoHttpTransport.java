package academy.llavor.academia.sso;

import jakarta.enterprise.context.ApplicationScoped;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static academy.llavor.academia.sso.SsoClientFailure.Code.INVALID_CONFIGURATION;

/** Native JSSE trust and HTTPS endpoint identification on the actual HTTP sockets. */
@ApplicationScoped
public class JdkSsoHttpTransport implements SsoHttpTransport {
    public void validateEnvironment() {
        if (!System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank()
                || !System.getProperty("javax.net.debug", "").isBlank()
                || Boolean.parseBoolean(System.getProperty("jdk.internal.httpclient.disableHostnameVerification"))
                || Boolean.parseBoolean(System.getProperty("jdk.httpclient.enableAllMethodRetry")))
            throw new SsoClientFailure(INVALID_CONFIGURATION);
    }

    HttpClient client(Duration timeout) {
        validateEnvironment();
        SSLParameters tls = new SSLParameters();
        tls.setEndpointIdentificationAlgorithm("HTTPS");
        tls.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        return HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
                .sslParameters(tls).proxy(new ProxySelector() {
                    public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
                }).build();
    }

    public SsoWireResponse exchange(HttpRequest request, Duration connectTimeout) throws IOException, InterruptedException {
        if (!"POST".equals(request.method()) || request.timeout().isEmpty())
            throw new SsoClientFailure(INVALID_CONFIGURATION);
        try (HttpClient http = client(connectTimeout)) {
            return exchange(http, request);
        }
    }

    private SsoWireResponse exchange(HttpClient http, HttpRequest request) throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, info -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = pending.get(request.timeout().orElseThrow().toMillis(), TimeUnit.MILLISECONDS);
            return new SsoWireResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new HttpTimeoutException("SSO response timeout");
        } catch (InterruptedException e) {
            pending.cancel(true); throw e;
        } catch (ExecutionException e) {
            pending.cancel(true);
            Throwable cause = e.getCause();
            for (int i = 0; cause != null && i < 12; i++, cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException) throw new HttpTimeoutException("SSO timeout");
                if (cause instanceof SSLException) throw new SSLHandshakeException("SSO TLS rejected");
            }
            throw new IOException("SSO transport failed");
        }
    }

    /** Cancels the subscription as soon as the actual body exceeds 4 KiB, including chunked bodies. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private final int limit;
        LimitedBody(){this(SsoWireResponse.LIMIT);}
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription=value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("SSO response limit")); return;
                }
                while (buffer.hasRemaining()) bytes.write(buffer.get());
            }
            subscription.request(1);
        }
        public void onError(Throwable ignored) { result.completeExceptionally(new IOException("SSO response failed")); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
