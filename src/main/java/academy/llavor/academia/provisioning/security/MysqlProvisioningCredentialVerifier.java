package academy.llavor.academia.provisioning.security;
import academy.llavor.academia.parameter.security.*;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
@ApplicationScoped
public class MysqlProvisioningCredentialVerifier implements ProvisioningCredentialVerifier {
 private LocalCredentialVerifier verifier;
 protected MysqlProvisioningCredentialVerifier(){}
 @Inject public MysqlProvisioningCredentialVerifier(LocalCredentialVerifier verifier){this.verifier=verifier;}
 @Override public boolean matches(Set<String> accepted,String keyId,String token){try{return verifier.matches(accepted,keyId,token);}
 catch(CredentialVerificationException failure){throw new ProvisioningFailure(failure.isTemporary()?ProvisioningFailure.Code.TEMPORARILY_UNAVAILABLE:ProvisioningFailure.Code.INVALID_CONFIGURATION);}
 catch(RuntimeException ignored){throw new ProvisioningFailure(ProvisioningFailure.Code.INVALID_CONFIGURATION);}}
}
