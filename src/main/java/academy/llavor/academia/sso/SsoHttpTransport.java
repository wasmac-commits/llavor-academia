package academy.llavor.academia.sso;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
/** Injectable boundary: synthetic tests never open a socket. */
public interface SsoHttpTransport {
    void validateEnvironment();
    SsoWireResponse exchange(HttpRequest request,Duration connectTimeout)throws IOException,InterruptedException;
}
