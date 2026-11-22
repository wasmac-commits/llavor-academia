package academy.llavor.academia.provisioning.security;
import java.util.Set;
/** Technical boundary for authentication; no verifier value enters the principal or config snapshot. */
public interface ProvisioningCredentialVerifier { boolean matches(Set<String> acceptedKeyIds,String keyId,String token); }
