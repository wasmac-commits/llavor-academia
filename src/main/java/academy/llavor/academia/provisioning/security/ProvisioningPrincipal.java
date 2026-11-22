package academy.llavor.academia.provisioning.security;

import java.security.Principal;
import java.util.UUID;

public final class ProvisioningPrincipal implements Principal {
    private final UUID academyId;
    private final UUID portalId;
    private final String academyCode;
    private final String keyId;
    ProvisioningPrincipal(UUID academyId, UUID portalId, String academyCode, String keyId) {
        this.academyId = academyId; this.portalId = portalId; this.academyCode = academyCode; this.keyId = keyId;
    }
    public UUID academyId() { return academyId; }
    public UUID portalId() { return portalId; }
    public String academyCode() { return academyCode; }
    @Override public String getName() { return keyId; }
}
