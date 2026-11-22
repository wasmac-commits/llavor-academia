package academy.llavor.academia.provisioning.config;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.*;
import java.util.*;
/** Independent application-scoped pin. Deliberately excludes JDBC/custom/password config sources. */
@ApplicationScoped
public class ExpectedInstallationIdentity {
 public static final String PROPERTY="llavor.academia.instance-id";
 private Config config;
 public ExpectedInstallationIdentity(){}
 public ExpectedInstallationIdentity(Config config){this.config=config;}
 public UUID require(UUID persisted){
  try{
   var current=config==null?ConfigProvider.getConfig():config;String value=null;
   for(var source:current.getConfigSources())if("Application".equals(source.getName())){value=source.getValue(PROPERTY);break;}
   UUID expected=academy.llavor.academia.provisioning.validation.ProvisioningValidation.parseUuid(value);
   if(!expected.equals(persisted))throw new IllegalArgumentException();return expected;
  }catch(RuntimeException ignored){throw new IllegalStateException("La identidad externa de instalacion no coincide o no esta configurada.");}
 }
}
