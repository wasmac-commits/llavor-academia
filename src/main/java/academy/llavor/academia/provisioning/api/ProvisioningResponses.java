package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.dto.ProvisioningError;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.ws.rs.core.*;

final class ProvisioningResponses {
    private ProvisioningResponses() {}
    static Response error(ProvisioningFailure failure) {
        var builder = Response.status(failure.getCode().status()).type(MediaType.APPLICATION_JSON_TYPE)
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
            .entity(new ProvisioningError(failure.getCode().name()));
        if (failure.getCode() == ProvisioningFailure.Code.UNAUTHORIZED)
            builder.header("WWW-Authenticate", "Bearer realm=\"llavor-provisioning\"");
        return builder.build();
    }
    static Response success(int status, Object entity) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").entity(entity).build();
    }
}
