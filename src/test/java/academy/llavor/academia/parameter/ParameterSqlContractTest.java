package academy.llavor.academia.parameter;

import academy.llavor.academia.parameter.entity.*;
import jakarta.persistence.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Static contracts only: these tests neither connect to nor emulate MySQL/MariaDB. */
class ParameterSqlContractTest {
    static final String SQL=read();
    static String read(){try{return Files.readString(Path.of("database/migrations/V002__instance_parameters.sql")).replaceAll("(?m)^--.*$","");}catch(Exception error){throw new AssertionError(error);}}
    static String table(String name){var matcher=Pattern.compile("CREATE TABLE "+name+" [(]([\\s\\S]*?)\\n[)] ENGINE").matcher(SQL);assertTrue(matcher.find(),name);return matcher.group(1);}
    static Map<String,String> columns(String name){
        Map<String,String> result=new LinkedHashMap<>();
        var matcher=Pattern.compile("(?m)^    ([a-z_]+) ((?:BIGINT|INT|TINYINT|VARCHAR|DATETIME)[^\\r\\n]*)").matcher(table(name));
        while(matcher.find())result.put(matcher.group(1),matcher.group(2));return result;
    }
    @Test void onlyNewTablesAndSafeSeedsInTheIntendedDatabase(){
        assertTrue(SQL.stripLeading().startsWith("USE llavor_academia;"));
        assertEquals(1,SQL.split("USE ",-1).length-1);
        assertFalse(Pattern.compile("(?m)^\\s*(DROP|TRUNCATE|ALTER|UPDATE|DELETE|GRANT|REVOKE|REPLACE|CREATE USER)\\b",Pattern.CASE_INSENSITIVE).matcher(SQL).find());
        assertFalse(SQL.contains("llavor_portal"));assertFalse(SQL.contains("ACADEMIA_001"));assertFalse(SQL.contains("AULA_001"));
        assertFalse(SQL.contains("INSERT INTO academy_instance"));assertFalse(SQL.contains("UUID()"));
        assertFalse(SQL.contains("INSERT INTO technical_academy_parameters"));
        assertTrue(SQL.contains("'llavor.academia.provisioning.enabled',"));
        assertTrue(SQL.contains("'llavor.academia.provisioning.allow-loopback-http',"));
        for(String line:SQL.split("\\R"))if(line.contains(".enabled',")||line.contains(".allow-loopback-http',"))assertTrue(line.contains("'BOOLEAN', 'false'"));
        assertEquals(2,SQL.split("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",-1).length-1);
        assertFalse(SQL.contains("global_permissions"));assertFalse(SQL.contains("INSERT INTO classrooms"));
    }
    @Test void everyPersistentJpaFieldMatchesSqlAndNoExtraColumnExists()throws Exception{
        for(Class<?> entity:List.of(AcademyParameter.class,academy.llavor.academia.installation.entity.AcademyInstance.class)){
            String name=entity.getAnnotation(Table.class).name();var definitions=columns(name);Set<String> found=new LinkedHashSet<>();
            assertNotNull(entity.getAnnotation(Entity.class));assertFalse(entity.getAnnotation(Cacheable.class).value());
            for(Class<?> current=entity;current!=Object.class;current=current.getSuperclass())for(Field field:current.getDeclaredFields()){
                if(Modifier.isStatic(field.getModifiers())||field.isAnnotationPresent(Transient.class))continue;
                Column column=field.getAnnotation(Column.class);JoinColumn join=field.getAnnotation(JoinColumn.class);
                String columnName=join!=null?join.name():column==null||column.name().isEmpty()?field.getName():column.name();
                found.add(columnName);String sql=definitions.get(columnName);assertNotNull(sql,name+"."+columnName);
                String type=join!=null?"BIGINT":field.getType()==LocalDateTime.class?"DATETIME(6)":field.getType()==boolean.class?"TINYINT(1)":
                    field.getType()==int.class?"INT":field.getType()==long.class||field.getType()==Long.class?"BIGINT":"VARCHAR("+column.length()+")";
                assertTrue(sql.startsWith(type),columnName+" : "+sql);
                assertEquals(join!=null?!join.nullable():!column.nullable(),sql.contains("NOT NULL"),columnName);
                if(field.getType().isEnum())assertEquals(EnumType.STRING,field.getAnnotation(Enumerated.class).value());
            }
            assertEquals(definitions.keySet(),found,name);
            for(var unique:entity.getAnnotation(Table.class).uniqueConstraints())
                assertTrue((table(name)+(name.equals("academy_instance")?
                    Files.readString(Path.of("database/migrations/V003__credential_installation_receipts.sql")):"")).contains(
                    "UNIQUE KEY "+unique.name()+" ("+String.join(", ",unique.columnNames())+")"));
            for(var index:entity.getAnnotation(Table.class).indexes())
                assertTrue(table(name).contains("KEY "+index.name()+" ("+index.columnList()+")"));
        }
        assertNotNull(AbstractParameter.class.getDeclaredField("version").getAnnotation(Version.class));
        assertFalse(AbstractParameter.class.getDeclaredField("key").getAnnotation(Column.class).updatable());
        assertNotNull(AbstractParameter.class.getDeclaredField("id").getAnnotation(GeneratedValue.class));
    }
    @Test void sensitivityKeepsJavaAttributeAndUsesNonreservedSqlIdentifier()throws Exception{
        var field=AbstractParameter.class.getDeclaredField("sensitive");
        assertEquals(boolean.class,field.getType());
        assertEquals("is_sensitive",field.getAnnotation(Column.class).name());
        assertFalse(field.getAnnotation(Column.class).nullable());
        assertFalse(field.getAnnotation(Column.class).updatable());
        assertFalse(Pattern.compile("\\bsensitive\\b").matcher(SQL).find());
        assertTrue(SQL.contains("is_sensitive TINYINT(1) NOT NULL"));
        assertNotNull(AbstractParameter.class.getMethod("isSensitive"));
    }
    @Test void closedTypesGroupsAndSecurityPolicyAreEncodedInChecks(){
        for(var type:ParameterType.values())assertTrue(SQL.contains("HEX('"+type.name()+"')"));
        for(var group:ParameterGroup.values())assertTrue(SQL.contains("HEX('"+group.name()+"')"));
        assertTrue(SQL.contains("is_sensitive = (value_type IN ('ENCRYPTED_SECRET','SHA256_VERIFIER'))"));
        assertTrue(SQL.contains("(critical=0 OR editable=0)"));assertTrue(SQL.contains("sort_order>=0 AND version>=0"));
        assertTrue(SQL.contains("param_key NOT IN ('llavor.portal.expected-instance-id'"));
        assertTrue(SQL.contains("CHAR_LENGTH(param_value)=64 AND param_value NOT REGEXP '[^0-9a-f]'"));
        assertTrue(SQL.contains("WHEN 'ENCRYPTED_SECRET' THEN param_value REGEXP '^v1[.]"));
        assertTrue(SQL.contains("MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),400)"));
    }
    @Test void numericAndBooleanSqlRulesAgreeWithJavaForBoundaryExamples(){
        // Execute only the literal regex in Java, supplemented by the same SQL BIGINT range.
        String integer=SQL.split("WHEN 'INTEGER' THEN CASE WHEN param_value REGEXP '")[1].split("'")[0];
        String decimal=SQL.split("WHEN 'DECIMAL' THEN param_value REGEXP '")[1].split("'")[0];
        for(String input:List.of("0","1","-1","9223372036854775807","-9223372036854775808","9223372036854775808","01","-0","1\\n")){
            boolean sql=input.matches(integer)&&!Pattern.compile("[^0-9-]").matcher(input).find();
            if(sql){var value=new java.math.BigInteger(input);sql=value.compareTo(java.math.BigInteger.valueOf(Long.MIN_VALUE))>=0&&value.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE))<=0;}
            assertEquals(sql,accepts(ParameterType.INTEGER,input),input);
        }
        for(String input:List.of("0","-0","1.2","1.20","0.0001","1e2","999999999999999.9999","1000000000000000"))
            assertEquals(input.matches(decimal)&&!input.equals("-0"),accepts(ParameterType.DECIMAL,input),input);
        for(String input:List.of("true","false","TRUE","false ","1"))assertEquals(Set.of("true","false").contains(input),accepts(ParameterType.BOOLEAN,input));
    }
    static boolean accepts(ParameterType type,String input){try{academy.llavor.academia.parameter.validation.ParameterValidation.value(type,input);return true;}catch(IllegalArgumentException expected){return false;}}
    @Test void localIdentityHasOneFixedRowAndNoGeneratedIdentityOrOrdinaryMutator()throws Exception{
        var type=academy.llavor.academia.installation.entity.AcademyInstance.class;
        assertNull(type.getDeclaredField("id").getAnnotation(GeneratedValue.class));
        for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()))assertFalse(field.getAnnotation(Column.class).updatable());
        assertTrue(table("academy_instance").contains("CHECK (id=1)"));assertTrue(SQL.contains("value_type<>'ENCRYPTED_SECRET'"));
        assertTrue(SQL.contains("value_type<>'UUID' AND param_group<>'IDENTITY'"));
        assertTrue(Arrays.stream(type.getMethods()).noneMatch(method->method.getName().startsWith("set")));
    }
}
