package academy.llavor.academia.parameter;
import academy.llavor.academia.parameter.entity.*;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import jakarta.persistence.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ParameterModelTest {
    static ParameterDefinition ordinary(ParameterType type){return new ParameterDefinition("general.value","Nombre",null,ParameterGroup.GENERAL,type,false,false,true,true,100);}
    static ParameterDefinition sensitive(ParameterType type){return new ParameterDefinition("llavor.academia.provisioning.keys.K01.verifier","Clave",null,ParameterGroup.SECURITY,type,true,true,false,true,10);}
    static AbstractParameter parameter(ParameterDefinition definition,String value){return new AcademyParameter(definition,value);}
    static void field(Object object,String name,Object value)throws Exception{
        Class<?> type=object.getClass();
        while(type!=null){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    static void lifecycle(Object object,Class<? extends Annotation> annotation)throws Exception{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())for(Method method:type.getDeclaredMethods())if(method.isAnnotationPresent(annotation)){
            try{method.setAccessible(true);method.invoke(object);}catch(InvocationTargetException error){if(error.getCause() instanceof RuntimeException cause)throw cause;throw error;}
        }
    }
    static java.util.stream.Stream<Arguments> valid(){return java.util.stream.Stream.of(
        Arguments.of(ParameterType.TEXT,"texto\ncon contexto"),Arguments.of(ParameterType.BOOLEAN,"true"),Arguments.of(ParameterType.BOOLEAN,"false"),
        Arguments.of(ParameterType.INTEGER,"-9223372036854775808"),Arguments.of(ParameterType.INTEGER,"9223372036854775807"),
        Arguments.of(ParameterType.DECIMAL,"-0.0001"),Arguments.of(ParameterType.DECIMAL,"999999999999999.9999"),
        Arguments.of(ParameterType.DATE,"2024-02-29"),Arguments.of(ParameterType.DATETIME,"2024-02-29T23:59:59.123456Z"),
        Arguments.of(ParameterType.UUID,"a453b620-e59b-11f1-87f2-d4939055b632"),
        Arguments.of(ParameterType.URL,"https://example.test/Context"),Arguments.of(ParameterType.EMAIL,"user@example.test"),
        Arguments.of(ParameterType.SHA256_VERIFIER,"a".repeat(64)),
        Arguments.of(ParameterType.ENCRYPTED_SECRET,"v1.M01."+"A".repeat(16)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[17])));}
    @ParameterizedTest @MethodSource("valid") void acceptsValidStoredFormats(ParameterType type,String value){assertEquals(value,ParameterValidation.value(type,value));}
    static java.util.stream.Stream<Arguments> invalid(){return java.util.stream.Stream.of(
        Arguments.of(ParameterType.BOOLEAN,"TRUE"),Arguments.of(ParameterType.BOOLEAN,"true "),Arguments.of(ParameterType.BOOLEAN,"1"),
        Arguments.of(ParameterType.INTEGER,"01"),Arguments.of(ParameterType.INTEGER,"-0"),Arguments.of(ParameterType.INTEGER,"9223372036854775808"),Arguments.of(ParameterType.INTEGER,"1\n"),
        Arguments.of(ParameterType.DECIMAL,"1.20"),Arguments.of(ParameterType.DECIMAL,"1e2"),Arguments.of(ParameterType.DECIMAL,"1.00001"),
        Arguments.of(ParameterType.DATE,"2023-02-29"),Arguments.of(ParameterType.DATE,"2024-04-31"),Arguments.of(ParameterType.DATE,"0001-01-01"),
        Arguments.of(ParameterType.DATETIME,"2024-01-01T24:00:00Z"),Arguments.of(ParameterType.DATETIME,"2024-01-01T00:00:00+01:00"),
        Arguments.of(ParameterType.DATETIME,"2024-01-01T00:00:00.1234567Z"),
        Arguments.of(ParameterType.UUID,"00000000-0000-0000-0000-000000000000"),Arguments.of(ParameterType.UUID,"A453B620-e59b-11f1-87f2-d4939055b632"),
        Arguments.of(ParameterType.URL,"https://user:password@example.test"),Arguments.of(ParameterType.URL,"https://example.test?token=PRIVATE"),Arguments.of(ParameterType.URL,"javascript:alert(1)"),
        Arguments.of(ParameterType.URL,"http://example.test:65536"),Arguments.of(ParameterType.URL,"https://EXAMPLE.test"),
        Arguments.of(ParameterType.EMAIL,"user@ localhost"),Arguments.of(ParameterType.SHA256_VERIFIER,"A".repeat(64)),
        Arguments.of(ParameterType.ENCRYPTED_SECRET,"plain-credential"),Arguments.of(ParameterType.ENCRYPTED_SECRET,"v1.M01."+"A".repeat(16)+"."+"A".repeat(22)),
        Arguments.of(ParameterType.TEXT,"x\0y"));}
    @ParameterizedTest @MethodSource("invalid") void rejectsMalformedValuesWithoutLeakingInput(ParameterType type,String value){
        var error=assertThrows(IllegalArgumentException.class,()->ParameterValidation.value(type,value));assertFalse(error.getMessage().contains(value));assertNull(error.getCause());}
    @ParameterizedTest @EnumSource(ParameterType.class) void unsetIsNotACredentialAndTypeStillRequired(ParameterType type){assertNull(ParameterValidation.value(type,null));}
    @Test void definitionAndLimitsAreStrict(){
        assertThrows(IllegalArgumentException.class,()->ParameterValidation.value(null,null));
        assertThrows(IllegalArgumentException.class,()->ParameterValidation.key(" general.key"));
        assertThrows(IllegalArgumentException.class,()->ParameterValidation.key("General.key"));
        assertThrows(IllegalArgumentException.class,()->ParameterValidation.key("a".repeat(121)));
        assertThrows(IllegalArgumentException.class,()->new ParameterDefinition("key","\u00a0\t",null,ParameterGroup.GENERAL,ParameterType.TEXT,false,false,true,true,0));
        assertThrows(IllegalArgumentException.class,()->new ParameterDefinition("key","Name",null,ParameterGroup.GENERAL,ParameterType.TEXT,false,false,true,true,-1));
        assertThrows(IllegalArgumentException.class,()->ParameterValidation.value(ParameterType.TEXT,"x".repeat(4001)));
        assertEquals(4000,ParameterValidation.value(ParameterType.TEXT,"\ud83d\ude00".repeat(4000)).codePointCount(0,8000));
        assertThrows(IllegalArgumentException.class,()->new ParameterDefinition("llavor.portal.provisioning.enabled","Enabled",null,ParameterGroup.PROVISIONING,ParameterType.TEXT,true,false,false,true,10));
        assertThrows(IllegalArgumentException.class,()->new ParameterDefinition("llavor.academia.instance-id","Identity",null,ParameterGroup.IDENTITY,ParameterType.UUID,true,false,false,true,10));
    }
    @Test void ordinaryEditsValidateAtomicallyAndUseUtcVersionAndImmutableCatalogue()throws Exception{
        var value=parameter(ordinary(ParameterType.INTEGER),"12");
        assertTrue(value.isActive());assertEquals(0,value.getVersion());lifecycle(value,PrePersist.class);lifecycle(value,PostPersist.class);
        assertEquals(value.getCreatedAt(),value.getUpdatedAt());assertTrue(Math.abs(Duration.between(LocalDateTime.now(Clock.systemUTC()),value.getCreatedAt()).toSeconds())<5);
        assertEquals(0,value.getCreatedAt().getNano()%1000);
        assertThrows(IllegalArgumentException.class,()->value.updateOrdinaryValue("bad","operator"));assertEquals("12",value.getAdministrativeValue());
        value.updateOrdinaryValue("13","user:1");value.setOrdinaryActive(false,"user:1");assertFalse(value.isActive());assertEquals("user:1",value.getModifiedBy());
        lifecycle(value,PreUpdate.class);field(value,"key","other.key");assertThrows(IllegalStateException.class,()->lifecycle(value,PreUpdate.class));
    }
    @Test void negativeVersionAndMalformedSqlValuesCannotBePersisted()throws Exception{
        var value=parameter(ordinary(ParameterType.BOOLEAN),"false");field(value,"version",-1L);assertThrows(IllegalStateException.class,()->lifecycle(value,PrePersist.class));
        field(value,"version",0L);field(value,"value","false ");assertThrows(IllegalArgumentException.class,()->lifecycle(value,PrePersist.class));
    }
    @Test void protectedRowsHaveNoOrdinaryMutationOrRawValueAccessor()throws Exception{
        var definition=new ParameterDefinition("llavor.academia.provisioning.enabled","Enabled",null,ParameterGroup.PROVISIONING,ParameterType.BOOLEAN,true,false,false,true,1);
        var value=parameter(definition,"false");assertThrows(IllegalStateException.class,()->value.updateOrdinaryValue("true","user:1"));
        assertThrows(NoSuchMethodException.class,()->AbstractParameter.class.getMethod("getValue"));
        for(var method:AbstractParameter.class.getMethods())assertFalse(Set.of("getSecret","getRawValue","getCiphertext").contains(method.getName()));
    }
@Test void verifierIsProtectedAndPlaintextCiphertextCredentialCannotBeStoredLocally(){
        var value=parameter(sensitive(ParameterType.SHA256_VERIFIER),"a".repeat(64));assertNull(value.getAdministrativeValue());
        assertThrows(IllegalArgumentException.class,()->parameter(new ParameterDefinition("secret","Secret",null,ParameterGroup.SECURITY,ParameterType.ENCRYPTED_SECRET,true,true,false,true,0),null));
    }
    @Test void identityIsExplicitSingletonWithoutRegenerationAndRejectsEveryUpdate()throws Exception{
        UUID academy=UUID.fromString("a45c4cdd-e59b-11f1-87f2-d4939055b632"),portal=UUID.fromString("a453b620-e59b-11f1-87f2-d4939055b632");
        var value=new academy.llavor.academia.installation.entity.AcademyInstance(academy,"ACADEMIA_001",portal);
        assertEquals(1L,value.getId());assertEquals(academy,value.getInstanceId());assertEquals(portal,value.getPortalInstanceId());
        lifecycle(value,PrePersist.class);assertEquals(0,value.getCreatedAt().getNano()%1000);value.requireExpectedInstanceId(academy);
        assertThrows(IllegalStateException.class,()->value.requireExpectedInstanceId(portal));assertThrows(IllegalStateException.class,()->lifecycle(value,PreUpdate.class));
        field(value,"id",2L);assertThrows(IllegalStateException.class,()->lifecycle(value,PrePersist.class));
        assertThrows(IllegalArgumentException.class,()->new academy.llavor.academia.installation.entity.AcademyInstance(new UUID(0,0),"ACA",portal));
        assertThrows(IllegalArgumentException.class,()->new academy.llavor.academia.installation.entity.AcademyInstance(academy,"aca",portal));
    }

}
