package academy.llavor.academia.provisioning.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

@ApplicationPath("/internal")
public class ProvisioningRestApplication extends Application {
    @Override public Set<Class<?>> getClasses() {
        return Set.of(AcademyIdentityResource.class, ClassroomProvisioningResource.class, ProvisioningAuthenticationFilter.class, ProvisioningExceptionMapper.class, ClassroomProvisioningRequestReader.class);
    }
}
