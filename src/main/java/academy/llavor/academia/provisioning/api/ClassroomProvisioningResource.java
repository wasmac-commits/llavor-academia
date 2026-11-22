package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.dto.ClassroomProvisioningRequest;
import academy.llavor.academia.provisioning.security.ProvisioningPrincipal;
import academy.llavor.academia.provisioning.service.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.UNAUTHORIZED;

@Path("/v1/academies/{academyId}/classrooms/{classroomId}")
@Produces(MediaType.APPLICATION_JSON)
@jakarta.enterprise.context.ApplicationScoped
public class ClassroomProvisioningResource {
    private ClassroomProvisioningService service;
    @Context private SecurityContext security;
    public ClassroomProvisioningResource() {}
    @Inject public ClassroomProvisioningResource(ClassroomProvisioningService service) { this.service = service; }

    private ProvisioningPrincipal principal() {
        if (security == null || !(security.getUserPrincipal() instanceof ProvisioningPrincipal principal)
                || !security.isUserInRole("LLAVOR_PROVISIONER"))
            throw new ProvisioningFailure(UNAUTHORIZED);
        return principal;
    }

    @PUT @Path("/provisioning-operations/{operationId}") @Consumes(MediaType.APPLICATION_JSON)
    public Response prepare(@PathParam("academyId") String academy, @PathParam("classroomId") String classroom,
                            @PathParam("operationId") String operation, ClassroomProvisioningRequest request) {
        var reply = service.prepare(principal(), academy, classroom, operation, request);
        // REQUIRES_NEW interceptor has completed its commit before service.prepare returns.
        return ProvisioningResponses.success(reply.httpStatus(), reply.result());
    }

    @GET @Path("/provisioning-operations/{operationId}")
    public Response operation(@PathParam("academyId") String academy, @PathParam("classroomId") String classroom,
                              @PathParam("operationId") String operation) {
        return ProvisioningResponses.success(200, service.getOperation(principal(), academy, classroom, operation));
    }

    @GET public Response classroom(@PathParam("academyId") String academy, @PathParam("classroomId") String classroom) {
        return ProvisioningResponses.success(200, service.getClassroom(principal(), academy, classroom));
    }
}
