package academy.llavor.academia.credential;
import academy.llavor.academia.credential.entity.*;
import jakarta.persistence.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static contract, no database, JPA bootstrap or simulated SQL constraint execution. */
class CredentialSqlContractTest {
    static final Class<?> ENTITY=CredentialInstallationReceipt.class;
    static String sql() throws Exception{return Files.readString(Path.of("database/migrations/V003__credential_installation_receipts.sql")).replaceAll("(?m)--.*$","");}
    static String compact(String text){return text.replaceAll("\\s+","");}
    @Test void eachPersistentAttributeHasMatchingSqlTypeLengthNullabilityAndUuidMapping() throws Exception {
        var columns=new HashMap<String,String>();var matcher=Pattern.compile("(?m)^    ([a-z_][a-z0-9_]*) (.+?)(?:,)?$").matcher(sql());
        while(matcher.find())columns.put(matcher.group(1),matcher.group(2));
        var mapped=new HashSet<String>();
        for(var field:ENTITY.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())||field.isAnnotationPresent(Transient.class))continue;
            var column=field.getAnnotation(Column.class);var join=field.getAnnotation(JoinColumn.class);
            String name=join!=null?join.name():column==null||column.name().isEmpty()?field.getName():column.name();
            mapped.add(name);String ddl=columns.get(name);assertNotNull(ddl,name);
            String type=join!=null||field.getType()==Long.class||field.getType()==long.class?"BIGINT":field.getType()==int.class||field.getType()==Integer.class?"INT":field.getType()==LocalDateTime.class?"DATETIME(6)":field.getType()==UUID.class?"VARCHAR(36)":"VARCHAR("+column.length()+")";
            assertTrue(ddl.startsWith(type),name+" "+ddl);
            if(column!=null&&!column.insertable()){assertTrue(ddl.contains("GENERATED ALWAYS"));assertFalse(column.updatable());}
            else if(join!=null||column==null||!column.nullable())assertTrue(ddl.contains("NOT NULL"),name);
            else assertTrue(ddl.contains(" NULL"),name);
            if(field.getType()==UUID.class)assertNotNull(field.getAnnotation(Convert.class));
            if(field.getType().isEnum()){
                assertEquals(EnumType.STRING,field.getAnnotation(Enumerated.class).value());
                for(var value:field.getType().getEnumConstants())assertTrue(sql().contains("HEX('"+value+"')"),value.toString());
            }
        }
        assertEquals(columns.keySet(),mapped);
    }
    @Test void uniquenessAndIndexesAreDeclaredInBothJpaAndSql() throws Exception {
        var table=ENTITY.getAnnotation(Table.class);String ddl=compact(sql());assertTrue(ddl.contains("CREATETABLE"+table.name()+"("));
        for(var unique:table.uniqueConstraints())assertTrue(ddl.contains("UNIQUEKEY"+unique.name()+"("+String.join(",",unique.columnNames())+")"),unique.name());
        for(var index:table.indexes())assertTrue(ddl.contains("KEY"+index.name()+"("+compact(index.columnList())+")"),index.name());
        assertTrue(ddl.contains("PRIMARYKEY(id)"));assertTrue(ddl.contains("idBIGINTNOTNULLAUTO_INCREMENT"));
        assertEquals(1,Arrays.stream(ENTITY.getDeclaredFields()).filter(f->f.isAnnotationPresent(Version.class)).count());
        assertTrue(ddl.contains("CHECK(version>=0)"));
    }
    @Test void associationsAreRequiredLazyWithoutCascadesAndHaveRestrictiveForeignKeys() throws Exception {
        String ddl=compact(sql());
        for(var field:ENTITY.getDeclaredFields())if(field.isAnnotationPresent(ManyToOne.class)){
            var relation=field.getAnnotation(ManyToOne.class);assertFalse(relation.optional());assertEquals(FetchType.LAZY,relation.fetch());assertEquals(0,relation.cascade().length);
            var join=field.getAnnotation(JoinColumn.class);assertFalse(join.nullable());assertFalse(join.updatable());
            String target=field.getType().getAnnotation(Table.class).name();
            assertTrue(ddl.contains("CONSTRAINT"+join.foreignKey().name()+"FOREIGNKEY("+join.name()+")REFERENCES"+target+"(id)ONDELETERESTRICTONUPDATERESTRICT"));
        }
    }
    @Test void noSecretsOrClassroomStateOrAutomaticSchemaGenerationAreIntroduced() throws Exception {
        for(var field:ENTITY.getDeclaredFields())assertFalse(field.getName().matches("(?i).*(token|password|verifier|envelope|masterKey).*"),field.getName());
        String source=Files.readString(Path.of("src/main/java/academy/llavor/academia/credential/entity/CredentialInstallationReceipt.java"));
        for(String forbidden:List.of("@Inject","@Startup","@Schedule","HttpClient","createQuery(","ClassroomProvisioningStatus","System.out","Logger"))assertFalse(source.contains(forbidden),forbidden);
        String pu=Files.readString(Path.of("src/main/resources/META-INF/persistence.xml"));
        assertTrue(pu.contains("schema-generation.database.action\" value=\"none"));assertTrue(pu.contains("eclipselink.ddl-generation\" value=\"none"));
        String clean=sql().replace("ON DELETE RESTRICT","").replace("ON UPDATE RESTRICT","");
        for(String word:List.of("INSERT","UPDATE","DELETE","DROP","TRUNCATE","GRANT","REVOKE"))assertFalse(Pattern.compile("\\b"+word+"\\b",Pattern.CASE_INSENSITIVE).matcher(clean).find(),word);
        assertTrue(sql().contains("USE llavor_academia;"));assertTrue(sql().contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"));
        assertFalse(sql().contains("allow-loopback-http"));assertFalse(sql().contains("ENCRYPTED_SECRET"));
    }
    @Test void compositeIdentityForeignKeyMatchesPermanentIdentityAndDoesNotRewriteItsData() throws Exception {
        String ddl=compact(sql());
        assertTrue(ddl.contains("ALTERTABLEacademy_instanceADDUNIQUEKEYuk_academy_instance_receipt_identity(id,instance_id,portal_instance_id,academy_code)"));
        assertTrue(ddl.contains("FOREIGNKEY(academy_instance_id,academy_instance_uuid,portal_instance_uuid,academy_code)REFERENCESacademy_instance(id,instance_id,portal_instance_id,academy_code)"));
        var uniques=academy.llavor.academia.installation.entity.AcademyInstance.class.getAnnotation(Table.class).uniqueConstraints();
        assertTrue(Arrays.stream(uniques).anyMatch(u->u.name().equals("uk_academy_instance_receipt_identity")&&Arrays.equals(u.columnNames(),new String[]{"id","instance_id","portal_instance_id","academy_code"})));
        assertFalse(sql().contains("llavor_portal"));assertFalse(sql().contains("classroom_provisioning_receipts"));
    }

}
