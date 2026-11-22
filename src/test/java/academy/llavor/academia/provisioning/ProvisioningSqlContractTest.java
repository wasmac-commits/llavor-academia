package academy.llavor.academia.provisioning;

import academy.llavor.academia.persistence.CanonicalUuidConverter;
import jakarta.persistence.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static JPA/SQL contracts only: never boots JPA, opens JDBC or executes migration statements. */
class ProvisioningSqlContractTest {
    private static String sql() throws Exception {
        return Files.readString(Path.of("database/migrations/V001__classroom_identity_and_provisioning.sql")).replaceAll("(?m)--.*$", "").strip();
    }
    private static String tableSql(Class<?> entity) throws Exception {
        String name = entity.getAnnotation(Table.class).name();
        Matcher matcher = Pattern.compile("CREATE TABLE " + name + " \\((.*?)\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;", Pattern.DOTALL).matcher(sql());
        assertTrue(matcher.find(), name);
        return matcher.group(1);
    }
    private static Map<String, String> columns(String sql) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("(?m)^    ([a-z_]+) ((?:BIGINT|INT|VARCHAR\\([0-9]+\\)|DATETIME\\(6\\))[^\\r\\n]*),").matcher(sql);
        while (matcher.find()) values.put(matcher.group(1), matcher.group(2));
        return values;
    }
    private static void matchesJpa(Class<?> entity) throws Exception {
        String table = tableSql(entity);
        Map<String, String> actual = columns(table);
        Set<String> expected = new HashSet<>();
        for (Field field : entity.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)) continue;
            Column column = field.getAnnotation(Column.class); JoinColumn join = field.getAnnotation(JoinColumn.class);
            String name = join != null ? join.name() : column == null || column.name().isEmpty() ? field.getName() : column.name();
            expected.add(name); String definition = actual.get(name);
            assertNotNull(definition, entity.getSimpleName() + "." + name);
            String type = field.getType() == UUID.class ? "VARCHAR(36)"
                : field.getType() == String.class || field.getType().isEnum() ? "VARCHAR(" + column.length() + ")"
                : field.getType() == LocalDateTime.class ? "DATETIME(6)" : field.getType() == int.class ? "INT" : "BIGINT";
            assertTrue(definition.startsWith(type), name + " " + definition);
            boolean required = field.isAnnotationPresent(Id.class) || (join != null ? !join.nullable() : !column.nullable());
            assertEquals(required, definition.contains("NOT NULL"), name);
            if (field.getType() == UUID.class) {
                assertEquals(CanonicalUuidConverter.class, field.getAnnotation(Convert.class).converter());
                assertTrue(definition.contains("CHARACTER SET ascii COLLATE ascii_bin"));
            }
            if (field.getType().isEnum()) assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
            if (field.isAnnotationPresent(Version.class)) assertTrue(definition.contains("DEFAULT 0"));
            if (field.isAnnotationPresent(GeneratedValue.class)) {
                assertEquals(GenerationType.IDENTITY, field.getAnnotation(GeneratedValue.class).strategy());
                assertTrue(definition.contains("AUTO_INCREMENT"));
            }
            if (join != null) {
                assertNotNull(field.getAnnotation(ManyToOne.class));
                assertEquals(FetchType.LAZY, field.getAnnotation(ManyToOne.class).fetch());
                assertEquals(0, field.getAnnotation(ManyToOne.class).cascade().length);
                assertTrue(table.contains("CONSTRAINT " + join.foreignKey().name() + " FOREIGN KEY (" + name + ")"));
            }
        }
        assertEquals(expected, actual.keySet());
        Table mapping = entity.getAnnotation(Table.class);
        for (UniqueConstraint key : mapping.uniqueConstraints())
            assertTrue(table.contains("UNIQUE KEY " + key.name() + " (" + String.join(", ", key.columnNames()) + ")"), key.name());
        for (Index key : mapping.indexes())
            assertTrue(table.contains("KEY " + key.name() + " (" + key.columnList() + ")"), key.name());
        assertFalse(entity.getAnnotation(Cacheable.class).value());
    }
    private static Set<String> enumValues(String constraint) throws Exception {
        String line = sql().lines().filter(l -> l.contains("CONSTRAINT " + constraint + " CHECK")).findFirst().orElseThrow();
        Matcher values = Pattern.compile("HEX\\('([A-Z_]+)'\\)").matcher(line);
        Set<String> result = new HashSet<>(); while (values.find()) result.add(values.group(1)); return result;
    }

    @Test void everyNewEntityColumnRelationshipIndexAndUniquenessMatchesSql() throws Exception {
        for (Class<?> entity : List.of(academy.llavor.academia.classroom.entity.LocalClassroom.class, academy.llavor.academia.provisioning.entity.ClassroomProvisioningReceipt.class)) matchesJpa(entity);
    }

    @Test void firstMigrationIsLocalOnlyAndCreatesNoSampleRowsOrForeignDatabaseLinks() throws Exception {
        String sql = sql();
        String[] statements = sql.split(";\\s*"); assertEquals(3, statements.length);
        assertEquals("USE llavor_academia", statements[0]);
        assertTrue(statements[1].strip().startsWith("CREATE TABLE classrooms ("));
        assertTrue(statements[2].strip().startsWith("CREATE TABLE classroom_provisioning_receipts ("));
        assertFalse(Pattern.compile("\\b(INSERT|UPDATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|TRIGGER|PROCEDURE)\\b",
            Pattern.CASE_INSENSITIVE).matcher(sql.replace("ON UPDATE RESTRICT", "")).find());
        assertFalse(sql.contains("llavor_portal"));
        String persistence = Files.readString(Path.of("src/main/resources/META-INF/persistence.xml"));
        assertTrue(persistence.contains("<jta-data-source>jdbc/LlavorAcademia</jta-data-source>"));
        assertTrue(persistence.contains("name=\"jakarta.persistence.schema-generation.database.action\" value=\"none\""));
        assertTrue(persistence.contains("name=\"eclipselink.ddl-generation\" value=\"none\""));
    }

    @Test void uniquenessAndCompositeContextFkPreventDuplicateOrContradictorySuccessfulReceipts() throws Exception {
        String sql = sql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("UNIQUE KEY uk_local_classroom_identity (portal_instance_id, portal_classroom_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_local_classroom_code (portal_instance_id, code)"));
        assertTrue(sql.contains("UNIQUE KEY uk_receipt_operation (portal_instance_id, operation_id)"));
        assertTrue(sql.contains("CONSTRAINT fk_receipt_classroom_context FOREIGN KEY (local_classroom_id, portal_instance_id, portal_classroom_id, technical_academy_id) REFERENCES classrooms (id, portal_instance_id, portal_classroom_id, technical_academy_id) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        assertTrue(sql.contains("result = 'READY' AND local_classroom_id IS NOT NULL AND error_code IS NULL"));
        assertTrue(sql.contains("result = 'FAILED' AND local_classroom_id IS NULL AND error_code IS NOT NULL AND error_code = 'PREPARATION_REJECTED'"));
        assertTrue(sql.contains("CHAR_LENGTH(request_hash) = 64 AND request_hash NOT REGEXP '[^0-9a-f]'"));
        assertEquals(Set.of("READY", "FAILED"), enumValues("ck_receipt_result"));
        assertEquals(Arrays.stream(academy.llavor.academia.provisioning.entity.ProvisioningErrorCode.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toSet()), enumValues("ck_receipt_error"));
    }
}
