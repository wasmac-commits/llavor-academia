package academy.llavor.academia.parameter;
import academy.llavor.academia.parameter.dto.*;
import academy.llavor.academia.parameter.entity.*;
import academy.llavor.academia.parameter.repository.*;
import academy.llavor.academia.parameter.service.*;
import academy.llavor.academia.parameter.audit.*;
import jakarta.persistence.*;
import jakarta.transaction.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.net.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ParameterInfrastructureTest {
 static void inject(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static java.util.stream.Stream<Arguments> typed(){return java.util.stream.Stream.of(
  Arguments.of(ParameterType.TEXT,"example",String.class),Arguments.of(ParameterType.BOOLEAN,"false",Boolean.class),Arguments.of(ParameterType.INTEGER,"18",Long.class),
  Arguments.of(ParameterType.DECIMAL,"1.25",BigDecimal.class),Arguments.of(ParameterType.URL,"https://example.test/Path",URI.class),
  Arguments.of(ParameterType.UUID,"a453b620-e59b-11f1-87f2-d4939055b632",UUID.class),Arguments.of(ParameterType.DATE,"2026-11-21",LocalDate.class),
  Arguments.of(ParameterType.DATETIME,"2026-11-21T12:00:00Z",OffsetDateTime.class),Arguments.of(ParameterType.EMAIL,"user@example.test",String.class));}
 @ParameterizedTest @MethodSource("typed") void typedReadsAreStrictAndDoNotUseFallbacks(ParameterType type,String value,Class<?> expected){
  var result=TypedParameterReader.decode(new ParameterSnapshot("key",type,true,false,value),type);assertEquals(ParameterReadResult.Status.VALID,result.status());assertInstanceOf(expected,result.value());
 }
 @Test void missingDisabledInvalidAndRestrictedAreDifferent(){
  assertEquals(ParameterReadResult.Status.MISSING,TypedParameterReader.decode(null,ParameterType.TEXT).status());
  assertEquals(ParameterReadResult.Status.DISABLED,TypedParameterReader.decode(new ParameterSnapshot("key",ParameterType.TEXT,false,false,"x"),ParameterType.TEXT).status());
  assertEquals(ParameterReadResult.Status.INVALID,TypedParameterReader.decode(new ParameterSnapshot("key",ParameterType.BOOLEAN,true,false,"TRUE"),ParameterType.BOOLEAN).status());
  assertEquals(ParameterReadResult.Status.INVALID,TypedParameterReader.decode(new ParameterSnapshot("key",ParameterType.TEXT,true,false,null),ParameterType.TEXT).status());
  assertEquals(ParameterReadResult.Status.INVALID,TypedParameterReader.decode(new ParameterSnapshot("key",ParameterType.TEXT,true,false,"true"),ParameterType.BOOLEAN).status());
  var sensitive=new ParameterSnapshot("key",ParameterType.SHA256_VERIFIER,true,true,"PRIVATE");assertNull(sensitive.value());assertFalse(sensitive.toString().contains("PRIVATE"));
  assertEquals(ParameterReadResult.Status.RESTRICTED,TypedParameterReader.decode(sensitive,ParameterType.TEXT).status());
  var view=new ParameterView(1L,"key","Name",null,ParameterGroup.SECURITY,ParameterType.SHA256_VERIFIER,true,false,true,true,true,1,"PRIVATE",null,null,0);
  assertNull(view.value());assertFalse(view.toString().contains("PRIVATE"));
 }
 @Test void repositoryPaginatesFiltersAndMasksInsideTheQuery()throws Exception{
  var em=mock(EntityManager.class);var repository=new AcademyParameterRepository();inject(repository,"em",em);
  @SuppressWarnings("unchecked") TypedQuery<ParameterView> query=mock(TypedQuery.class);
  when(em.createQuery(anyString(),eq(ParameterView.class))).thenReturn(query);when(query.setParameter(anyString(),any())).thenReturn(query);when(query.setFirstResult(anyInt())).thenReturn(query);when(query.setMaxResults(anyInt())).thenReturn(query);when(query.getResultList()).thenReturn(List.of());
  var filter=new ParameterFilter("a%_!",ParameterGroup.GENERAL,ParameterType.TEXT,true);
  repository.findPage(filter,20,10);
  var text=ArgumentCaptor.forClass(String.class);verify(em).createQuery(text.capture(),eq(ParameterView.class));assertTrue(text.getValue().contains("CASE WHEN p.sensitive = FALSE THEN p.value ELSE NULL END"));
  verify(query).setFirstResult(20);verify(query).setMaxResults(10);verify(query).setParameter("text","%a!%!_!!%");verify(query).setParameter("group",ParameterGroup.GENERAL);verify(query).setParameter("type",ParameterType.TEXT);verify(query).setParameter("active",true);
  assertThrows(IllegalArgumentException.class,()->repository.findPage(filter,0,101));
  assertThrows(IllegalArgumentException.class,()->repository.readBatch(Collections.nCopies(101,"key")));
  assertTrue(repository.readBatch(List.of()).isEmpty());
  assertTrue(repository.findByKey("key").isEmpty());verify(query).setParameter("key","key");
 }
 @Test void auditIsMetadataOnlyAndOnlyEmittedAfterSuccessfulCommit()throws Exception{
  var audit=new ParameterAudit();var registry=mock(TransactionSynchronizationRegistry.class);var sink=mock(ParameterAuditSink.class);
  inject(audit,"registry",registry);inject(audit,"sink",sink);when(registry.getTransactionKey()).thenReturn(new Object());
  audit.record("user:1","GLOBAL",null,"general.name",ParameterAudit.Operation.UPDATE_ORDINARY);
  var sync=ArgumentCaptor.forClass(Synchronization.class);verify(registry).registerInterposedSynchronization(sync.capture());verifyNoInteractions(sink);
  sync.getValue().afterCompletion(Status.STATUS_ROLLEDBACK);verifyNoInteractions(sink);
  sync.getValue().afterCompletion(Status.STATUS_COMMITTED);var event=ArgumentCaptor.forClass(ParameterAudit.Entry.class);verify(sink).committed(event.capture());
  assertEquals("user:1",event.getValue().actor());assertEquals("general.name",event.getValue().key());
  assertTrue(Arrays.stream(ParameterAudit.Entry.class.getRecordComponents()).noneMatch(c->c.getName().matches(".*(?i:value|hash|secret|credential).*")));
  assertThrows(IllegalArgumentException.class,()->audit.record("actor\nINJECT","GLOBAL",null,"key",ParameterAudit.Operation.UPDATE_ORDINARY));
  when(registry.getTransactionKey()).thenReturn(null);assertThrows(IllegalStateException.class,()->audit.record("user:1","GLOBAL",null,"key",ParameterAudit.Operation.UPDATE_ORDINARY));
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 @Test void newQueriesPassTheInstalledEclipseLinkGrammarWithoutDatabaseAccess()throws Exception{
  Path jar=Path.of(System.getProperty("llavor.test.jpql.jar","C:/payara6/glassfish/modules/org.eclipse.persistence.jpa.jpql.jar"));assertTrue(Files.isRegularFile(jar));
  List<String> queries=new ArrayList<>();var em=mock(EntityManager.class);
  @SuppressWarnings("rawtypes") TypedQuery query=mock(TypedQuery.class);
  when(query.setParameter(anyString(),any())).thenReturn(query);when(query.setFirstResult(anyInt())).thenReturn(query);when(query.setMaxResults(anyInt())).thenReturn(query);when(query.getResultList()).thenReturn(List.of());when(query.getSingleResult()).thenReturn(0L);
  doAnswer(call->{queries.add(call.getArgument(0));return query;}).when(em).createQuery(anyString(),any(Class.class));
  var repository=new AcademyParameterRepository();inject(repository,"em",em);repository.findPage(new ParameterFilter("name",ParameterGroup.GENERAL,ParameterType.TEXT,true),0,10);repository.count(ParameterFilter.ALL);repository.findById(1L);repository.findByKey("key");repository.readBatch(List.of("key"));repository.findForUpdate(1L);
  var secret=new academy.llavor.academia.parameter.security.JpaAcademyVerifierParameterReader();inject(secret,"em",em);assertThrows(ParameterAccessException.class,()->secret.read("K01"));
  try(var loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},getClass().getClassLoader())){
   var grammarType=loader.loadClass("org.eclipse.persistence.jpa.jpql.parser.JPQLGrammar");var grammar=loader.loadClass("org.eclipse.persistence.jpa.jpql.parser.JPQLGrammar2_1").getMethod("instance").invoke(null);
   var expression=loader.loadClass("org.eclipse.persistence.jpa.jpql.parser.JPQLExpression");var helperType=loader.loadClass("org.eclipse.persistence.jpa.jpql.tools.DefaultJPQLQueryHelper");
   for(String jpql:queries){var helper=helperType.getConstructor(grammarType).newInstance(grammar);var parsed=expression.getConstructor(CharSequence.class,grammarType).newInstance(jpql,grammar);
    helperType.getMethod("setJPQLExpression",expression).invoke(helper,parsed);var problems=(List<?>)helperType.getMethod("validateGrammar").invoke(helper);assertTrue(problems.isEmpty(),jpql+" : "+problems);
   }
  }
 }
 @Test void configurationBatchesReadOnceAndReturnsSafeErrors(){
  var repo=mock(AcademyParameterRepository.class);var service=new ParameterConfigurationService(repo);
  when(repo.readBatch(List.of("key"))).thenReturn(Map.of("key",new ParameterSnapshot("key",ParameterType.INTEGER,true,false,"12")));
  assertEquals(12L,service.integer("key").value());
  assertEquals(ParameterReadResult.Status.VALID,service.readBatch(List.of("key")).get("key").status());
  when(repo.readBatch(any())).thenThrow(new PersistenceException("PRIVATE"));var error=assertThrows(ParameterAccessException.class,()->service.integer("key"));assertFalse(error.getMessage().contains("PRIVATE"));assertNull(error.getCause());
 }
@Test void dedicatedSensitiveReaderValidatesPolicyAndDoesNotExposeMaterial()throws Exception{
  var em=mock(EntityManager.class);var reader=new academy.llavor.academia.parameter.security.JpaAcademyVerifierParameterReader();inject(reader,"em",em);
  @SuppressWarnings("unchecked") TypedQuery<Object[]> query=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(Object[].class))).thenReturn(query);when(query.setParameter(anyString(),any())).thenReturn(query);when(query.setMaxResults(anyInt())).thenReturn(query);
  String payload="a".repeat(64);when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{ParameterType.SHA256_VERIFIER,true,true,true,false,payload}));
  var material=reader.read("K01");assertFalse(material.toString().contains(payload));assertEquals(payload,material.use(v->v));
  assertFalse(java.io.Serializable.class.isAssignableFrom(material.getClass()));assertTrue(Arrays.stream(material.getClass().getDeclaredMethods()).noneMatch(m->m.getName().startsWith("get")));
  when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{ParameterType.SHA256_VERIFIER,false,true,true,false,payload}));assertEquals(ParameterAccessException.Reason.FORBIDDEN,assertThrows(ParameterAccessException.class,()->reader.read("K01")).getReason());
  when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{ParameterType.TEXT,true,false,false,true,"PRIVATE"}));assertThrows(ParameterAccessException.class,()->reader.read("K01"));
  when(query.getResultList()).thenReturn(List.of());assertEquals(ParameterAccessException.Reason.NOT_FOUND,assertThrows(ParameterAccessException.class,()->reader.read("K01")).getReason());
 }

}
