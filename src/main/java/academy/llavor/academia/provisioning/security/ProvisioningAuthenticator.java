package academy.llavor.academia.provisioning.security;
import academy.llavor.academia.provisioning.config.AcademiaIdentity;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;
@ApplicationScoped
public class ProvisioningAuthenticator {
 private ProvisioningCredentialVerifier verifiers;
 protected ProvisioningAuthenticator(){}
 @Inject public ProvisioningAuthenticator(ProvisioningCredentialVerifier verifiers){this.verifiers=verifiers;}
 public ProvisioningPrincipal authenticate(AcademiaIdentity identity,String authorization,String key){try{
  if(identity==null||authorization==null||key==null||!identity.verifierParameters().containsKey(key)||authorization.length()!=50||!authorization.regionMatches(true,0,"Bearer ",0,7))throw new ProvisioningFailure(UNAUTHORIZED);
  String token=authorization.substring(7);
  if(!token.matches("[A-Za-z0-9_-]{43}")||!verifiers.matches(identity.verifierParameters().keySet(),key,token))throw new ProvisioningFailure(UNAUTHORIZED);
  return new ProvisioningPrincipal(identity.academyId(),identity.portalId(),identity.academyCode(),key);
 }catch(ProvisioningFailure safe){throw safe;}catch(RuntimeException ignored){throw new ProvisioningFailure(INVALID_CONFIGURATION);}}
}
