package academy.llavor.academia.provisioning.config;

import java.util.Map;
import java.util.UUID;

public record AcademiaIdentity(UUID academyId, String academyCode, UUID portalId,
                               Map<String, String> verifierParameters, boolean allowLoopbackHttp) {
    public AcademiaIdentity { verifierParameters = Map.copyOf(verifierParameters); }
}
