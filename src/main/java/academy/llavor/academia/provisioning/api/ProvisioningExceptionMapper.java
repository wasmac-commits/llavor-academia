package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;

@Provider
public class ProvisioningExceptionMapper implements ExceptionMapper<Exception> {
    @Override public Response toResponse(Exception exception) {
        if (exception instanceof ProvisioningFailure failure) return ProvisioningResponses.error(failure);
        if (exception instanceof BadRequestException) return ProvisioningResponses.error(new ProvisioningFailure(INVALID_REQUEST));
        if (exception instanceof NotFoundException) return ProvisioningResponses.error(new ProvisioningFailure(NOT_FOUND));
        if (exception instanceof WebApplicationException web) {
            int status = web.getResponse().getStatus();
            if (status == 405 || status == 406 || status == 415)
                return ProvisioningResponses.success(status, new academy.llavor.academia.provisioning.dto.ProvisioningError("INVALID_REQUEST"));
        }
        return ProvisioningResponses.error(new ProvisioningFailure(TEMPORARILY_UNAVAILABLE));
    }
}
