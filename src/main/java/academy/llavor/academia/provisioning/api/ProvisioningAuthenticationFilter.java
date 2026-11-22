package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.config.*;
import academy.llavor.academia.provisioning.security.*;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import java.io.*;
import java.security.Principal;
import java.util.List;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;

@Provider @PreMatching @Priority(Priorities.AUTHENTICATION)
@jakarta.enterprise.context.ApplicationScoped
public class ProvisioningAuthenticationFilter implements ContainerRequestFilter {
    static final int MAX_BODY_BYTES = 16 * 1024;
    @Inject private AcademiaProvisioningConfiguration configuration;
    @Inject private ProvisioningAuthenticator authenticator;
    @Context private HttpServletRequest servletRequest;

    public ProvisioningAuthenticationFilter() {}
    ProvisioningAuthenticationFilter(AcademiaProvisioningConfiguration configuration, ProvisioningAuthenticator authenticator,
                                    HttpServletRequest servletRequest) {
        this.configuration = configuration; this.authenticator = authenticator; this.servletRequest = servletRequest;
    }

    @Override public void filter(ContainerRequestContext request) {
        try {
            AcademiaIdentity identity = configuration.loadEnabled();
            boolean secure = request.getSecurityContext() != null && request.getSecurityContext().isSecure();
            // Host headers alone cannot authorize a plaintext connection.
            if (!secure && !(identity.allowLoopbackHttp() && loopback(servletRequest == null ? null : servletRequest.getRemoteAddr())
                    && loopback(request.getUriInfo().getRequestUri().getHost())))
                throw new ProvisioningFailure(FORBIDDEN);
            String authorization = single(request, "Authorization"), key = single(request, "X-Llavor-Key-Id");
            ProvisioningPrincipal principal = authenticator.authenticate(identity, authorization, key);
            request.setSecurityContext(new SecurityContext() {
                @Override public Principal getUserPrincipal() { return principal; }
                @Override public boolean isUserInRole(String role) { return "LLAVOR_PROVISIONER".equals(role); }
                @Override public boolean isSecure() { return secure; }
                @Override public String getAuthenticationScheme() { return "Bearer"; }
            });
            String length = request.getHeaderString("Content-Length");
            if (length != null) {
                try {
                    long size = Long.parseLong(length);
                    if (size < 0 || size > MAX_BODY_BYTES) throw new ProvisioningFailure(INVALID_REQUEST);
                } catch (NumberFormatException ignored) { throw new ProvisioningFailure(INVALID_REQUEST); }
            }
            if (request.hasEntity()) request.setEntityStream(new LimitedBody(request.getEntityStream()));
        } catch (ProvisioningFailure failure) { request.abortWith(ProvisioningResponses.error(failure)); }
        catch (RuntimeException ignored) { request.abortWith(ProvisioningResponses.error(new ProvisioningFailure(INVALID_CONFIGURATION))); }
    }

    private static String single(ContainerRequestContext request, String name) {
        List<String> values = request.getHeaders().get(name);
        if (values == null || values.size() != 1) throw new ProvisioningFailure(UNAUTHORIZED);
        return values.getFirst();
    }
    private static boolean loopback(String host) {
        return "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host) || "localhost".equalsIgnoreCase(host)
            || "0:0:0:0:0:0:0:1".equals(host);
    }
    static final class LimitedBody extends FilterInputStream {
        private int read;
        LimitedBody(InputStream input) { super(input); }
        @Override public int read() throws IOException {
            int value = in.read(); if (value != -1 && ++read > MAX_BODY_BYTES) throw new IOException("Invalid request size.");
            return value;
        }
        @Override public long skip(long count) throws IOException {
            if (count <= 0) return 0;
            byte[] buffer = new byte[(int)Math.min(1024, count)];
            long skipped = 0;
            while (skipped < count) {
                int consumed = read(buffer, 0, (int)Math.min(buffer.length, count - skipped));
                if (consumed < 0) break;
                skipped += consumed;
            }
            return skipped;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, Math.min(length, Math.max(1, MAX_BODY_BYTES - read + 1)));
            if (count > 0 && (read += count) > MAX_BODY_BYTES) throw new IOException("Invalid request size.");
            return count;
        }
    }
}
