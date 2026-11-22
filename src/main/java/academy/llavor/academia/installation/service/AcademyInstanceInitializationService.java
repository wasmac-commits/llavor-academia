package academy.llavor.academia.installation.service;
import academy.llavor.academia.installation.dto.AcademyInstanceView;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.provisioning.repository.ClassroomProvisioningReceiptRepository;
import academy.llavor.academia.parameter.audit.ParameterAudit;
import academy.llavor.academia.parameter.service.ParameterAccessException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.*;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import java.util.*;
import java.sql.SQLException;
import static academy.llavor.academia.parameter.service.ParameterAccessException.Reason.*;
/** Explicit local administrative call only. No startup callback, timer, REST or JSF endpoint.
 * Deployment must grant the bootstrap role explicitly; it is never mapped to ordinary users here.
 */
@Stateless @LocalBean @DeclareRoles("LLAVOR_INSTANCE_BOOTSTRAP") @RolesAllowed("LLAVOR_INSTANCE_BOOTSTRAP")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AcademyInstanceInitializationService {
 @Resource private SessionContext context;
 @Inject private AcademyInstanceRepository identities;
 @Inject private LocalClassroomRepository classrooms;
 @Inject private ClassroomProvisioningReceiptRepository receipts;
 @Inject private ParameterAudit audit;
 public AcademyInstanceView initialize(UUID instanceId,String academyCode,UUID portalId){try{
  if(!context.isCallerInRole("LLAVOR_INSTANCE_BOOTSTRAP"))throw new ParameterAccessException(FORBIDDEN);
  String actor=context.getCallerPrincipal().getName();
  if(actor==null||!actor.matches("[A-Za-z0-9_.:@-]{1,190}"))throw new ParameterAccessException(FORBIDDEN);
  if(identities.count()!=0||identities.find().isPresent())throw new ParameterAccessException(ALREADY_INITIALIZED);
  var identity=new AcademyInstance(instanceId,academyCode,portalId); // Caller supplies the permanent UUIDs. No random generator.
  if(classrooms.containsOtherInstance(portalId,instanceId)||receipts.containsOtherInstance(portalId,instanceId))throw new ParameterAccessException(INVALID_DATA);
  identities.create(identity); // Singleton PK rejects a simultaneous second initializer. No merge/update/retry.
  audit.record(actor,"INSTANCE",instanceId.toString(),"academy.instance",ParameterAudit.Operation.INITIALIZE_IDENTITY);
  return AcademyInstanceView.from(identity);
 }catch(ParameterAccessException safe){throw safe;}catch(IllegalArgumentException ignored){throw new ParameterAccessException(INVALID_DATA);}
 catch(RuntimeException failure){
  Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
  for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause())if(cause instanceof SQLException sql&&sql.getErrorCode()==1062)throw new ParameterAccessException(ALREADY_INITIALIZED);
  throw new ParameterAccessException(STORAGE_FAILURE);
 }}
}
