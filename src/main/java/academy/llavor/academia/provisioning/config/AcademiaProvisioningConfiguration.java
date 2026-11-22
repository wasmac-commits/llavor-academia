package academy.llavor.academia.provisioning.config;
import academy.llavor.academia.parameter.dto.ParameterReadResult;
import academy.llavor.academia.parameter.service.*;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.PersistenceException;
import java.util.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;
/** Local MySQL identity and settings; the independent application pin remains external. */
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class AcademiaProvisioningConfiguration {
 public static final String PREFIX="llavor.academia.provisioning.";
 public static final List<String> KEYS=List.of(PREFIX+"enabled",PREFIX+"allow-loopback-http",PREFIX+"accepted-key-ids");
 private ParameterConfigurationService parameters;private AcademyInstanceRepository installation;private ExpectedInstallationIdentity pin;
 protected AcademiaProvisioningConfiguration(){}
 @Inject public AcademiaProvisioningConfiguration(ParameterConfigurationService parameters,AcademyInstanceRepository installation,ExpectedInstallationIdentity pin){this.parameters=parameters;this.installation=installation;this.pin=pin;}
 public AcademiaIdentity loadEnabled(){try{
  var values=parameters.readBatch(KEYS);var enabled=values.get(PREFIX+"enabled");
  if(enabled==null||enabled.status()==ParameterReadResult.Status.MISSING||enabled.status()==ParameterReadResult.Status.DISABLED)throw new ProvisioningFailure(PROVISIONING_DISABLED);
  if(!required(values,PREFIX+"enabled",Boolean.class))throw new ProvisioningFailure(PROVISIONING_DISABLED);
  var identity=installation.find().orElseThrow(()->new ProvisioningFailure(INVALID_CONFIGURATION));pin.require(identity.getInstanceId());
  ProvisioningValidation.uuid(identity.getInstanceId());ProvisioningValidation.uuid(identity.getPortalInstanceId());
  if(!identity.getAcademyCode().equals(ProvisioningValidation.code(identity.getAcademyCode())))throw new ProvisioningFailure(INVALID_CONFIGURATION);
  String keys=required(values,PREFIX+"accepted-key-ids",String.class);String[] names=keys.split(",",-1);
  if(names.length<1||names.length>4)throw new ProvisioningFailure(INVALID_CONFIGURATION);
  Map<String,String> references=new LinkedHashMap<>();
  for(String entry:names){String key=entry.strip();if(!key.matches("[A-Za-z0-9_-]{1,32}")||references.putIfAbsent(key,PREFIX+"keys."+key+".verifier")!=null)throw new ProvisioningFailure(INVALID_CONFIGURATION);}
  return new AcademiaIdentity(identity.getInstanceId(),identity.getAcademyCode(),identity.getPortalInstanceId(),references,required(values,PREFIX+"allow-loopback-http",Boolean.class));
 }catch(ProvisioningFailure safe){throw safe;}catch(ParameterAccessException failure){throw new ProvisioningFailure(failure.getReason()==ParameterAccessException.Reason.STORAGE_FAILURE?TEMPORARILY_UNAVAILABLE:INVALID_CONFIGURATION);}catch(PersistenceException ignored){throw new ProvisioningFailure(TEMPORARILY_UNAVAILABLE);}catch(RuntimeException ignored){throw new ProvisioningFailure(INVALID_CONFIGURATION);}}
 private static <T>T required(Map<String,ParameterReadResult<Object>> values,String key,Class<T> type){var value=values.get(key);if(value==null||value.status()!=ParameterReadResult.Status.VALID||!type.isInstance(value.value()))throw new ProvisioningFailure(INVALID_CONFIGURATION);return type.cast(value.value());}
}
