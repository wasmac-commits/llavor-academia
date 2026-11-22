package academy.llavor.academia.provisioning.dto;

import academy.llavor.academia.provisioning.security.ProvisioningPrincipal;

/** Authenticated installation identity only. Never a credential or a provisioning result. */
public final class AcademyIdentityResult {
    private final String portalInstanceId, academyId, academyCode, keyId;

    public AcademyIdentityResult(ProvisioningPrincipal principal) {
        portalInstanceId = principal.portalId().toString();
        academyId = principal.academyId().toString();
        academyCode = principal.academyCode();
        keyId = principal.getName();
    }

    public int getContractVersion() { return 1; }
    public String getPortalInstanceId() { return portalInstanceId; }
    public String getAcademyId() { return academyId; }
    public String getAcademyCode() { return academyCode; }
    public String getKeyId() { return keyId; }
}
