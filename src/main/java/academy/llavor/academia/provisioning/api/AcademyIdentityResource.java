package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.security.ProvisioningPrincipal;
import academy.llavor.academia.provisioning.service.ClassroomProvisioningService;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.UNAUTHORIZED;

/** Read-only identity probe; uses the same machine authentication as classroom provisioning. */
@Path("/v1/academies/{academyId}/identity")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AcademyIdentityResource {
    private ClassroomProvisioningService service;
    @Context private SecurityContext security;

    public AcademyIdentityResource() {}
    @Inject public AcademyIdentityResource(ClassroomProvisioningService service) { this.service = service; }

    @GET
    public Response identity(@PathParam("academyId") String academy) {
        if (security == null || !(security.getUserPrincipal() instanceof ProvisioningPrincipal principal)
                || !security.isUserInRole("LLAVOR_PROVISIONER"))
            throw new ProvisioningFailure(UNAUTHORIZED);
        return ProvisioningResponses.success(200, service.getIdentity(principal, academy));
    }
}
