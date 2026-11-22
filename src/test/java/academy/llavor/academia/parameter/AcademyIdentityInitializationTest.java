package academy.llavor.academia.parameter;
import academy.llavor.academia.installation.entity.*;
import academy.llavor.academia.installation.repository.*;
import academy.llavor.academia.installation.service.*;
import academy.llavor.academia.classroom.repository.*;
import academy.llavor.academia.provisioning.repository.*;
import academy.llavor.academia.parameter.audit.*;
import academy.llavor.academia.parameter.service.*;
import jakarta.ejb.*;
import jakarta.annotation.security.*;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AcademyIdentityInitializationTest {
 UUID academy=UUID.fromString("a45c4cdd-e59b-11f1-87f2-d4939055b632"),portal=UUID.fromString("a453b620-e59b-11f1-87f2-d4939055b632");
 SessionContext context=mock(SessionContext.class);AcademyInstanceRepository identities=mock(AcademyInstanceRepository.class);
 LocalClassroomRepository classrooms=mock(LocalClassroomRepository.class);ClassroomProvisioningReceiptRepository receipts=mock(ClassroomProvisioningReceiptRepository.class);ParameterAudit audit=mock(ParameterAudit.class);
 AcademyInstanceInitializationService service=new AcademyInstanceInitializationService();
 @BeforeEach void setup()throws Exception{
  ParameterInfrastructureTest.inject(service,"context",context);ParameterInfrastructureTest.inject(service,"identities",identities);ParameterInfrastructureTest.inject(service,"classrooms",classrooms);ParameterInfrastructureTest.inject(service,"receipts",receipts);ParameterInfrastructureTest.inject(service,"audit",audit);
  when(context.isCallerInRole("LLAVOR_INSTANCE_BOOTSTRAP")).thenReturn(true);when(context.getCallerPrincipal()).thenReturn(()->"local-admin");when(identities.find()).thenReturn(Optional.empty());when(identities.create(any())).thenAnswer(c->c.getArgument(0));
 }
 @Test void unauthorizedInitializationIsDeniedBeforeAnyReadOrWrite(){
  when(context.isCallerInRole(anyString())).thenReturn(false);assertEquals(ParameterAccessException.Reason.FORBIDDEN,assertThrows(ParameterAccessException.class,()->service.initialize(academy,"ACADEMIA_001",portal)).getReason());
  verifyNoInteractions(identities,classrooms,receipts,audit);
  assertArrayEquals(new String[]{"LLAVOR_INSTANCE_BOOTSTRAP"},AcademyInstanceInitializationService.class.getAnnotation(RolesAllowed.class).value());assertNotNull(AcademyInstanceInitializationService.class.getAnnotation(Stateless.class));
 }
 @Test void explicitInitializationUsesSuppliedIdentifiersAndSecondCallCannotReplaceThem(){
  var result=service.initialize(academy,"ACADEMIA_001",portal);assertEquals(academy,result.instanceId());assertEquals(portal,result.portalInstanceId());
  verify(audit).record("local-admin","INSTANCE",academy.toString(),"academy.instance",ParameterAudit.Operation.INITIALIZE_IDENTITY);
  when(identities.count()).thenReturn(1L);assertEquals(ParameterAccessException.Reason.ALREADY_INITIALIZED,assertThrows(ParameterAccessException.class,()->service.initialize(portal,"OTHER",academy)).getReason());
  verify(identities,times(1)).create(any());
 }
 @Test void contradictoryExistingDataAndBadIdentityNeverPersist(){
  assertThrows(ParameterAccessException.class,()->service.initialize(new UUID(0,0),"ACA",portal));
  when(classrooms.containsOtherInstance(portal,academy)).thenReturn(true);assertEquals(ParameterAccessException.Reason.INVALID_DATA,assertThrows(ParameterAccessException.class,()->service.initialize(academy,"ACA",portal)).getReason());
  verify(identities,never()).create(any());verifyNoInteractions(audit);
 }
 @Test void simultaneousPrimaryKeyRaceIsRejectedWithoutRetryOrAudit(){
  when(identities.create(any())).thenThrow(new PersistenceException("PRIVATE",new SQLException("PRIVATE","23000",1062)));
  var error=assertThrows(ParameterAccessException.class,()->service.initialize(academy,"ACADEMIA_001",portal));assertEquals(ParameterAccessException.Reason.ALREADY_INITIALIZED,error.getReason());assertNull(error.getCause());verify(identities,times(1)).create(any());verifyNoInteractions(audit);
 }
 @Test void identityRepositoryOnlyPersistsSingletonAndNeverMerges()throws Exception{
  var repository=new AcademyInstanceRepository();var em=mock(EntityManager.class);ParameterInfrastructureTest.inject(repository,"em",em);
  var identity=new AcademyInstance(academy,"ACA",portal);assertSame(identity,repository.create(identity));verify(em).persist(identity);verify(em).flush();verify(em,never()).merge(any());
  assertEquals(Transactional.TxType.MANDATORY,AcademyInstanceRepository.class.getMethod("create",AcademyInstance.class).getAnnotation(Transactional.class).value());
  assertNotNull(ParameterAccessException.class.getAnnotation(ApplicationException.class));assertTrue(ParameterAccessException.class.getAnnotation(ApplicationException.class).rollback());
 }
}
