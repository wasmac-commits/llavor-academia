package academy.llavor.academia.provisioning;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.persistence.CanonicalUuidConverter;
import academy.llavor.academia.provisioning.entity.*;
import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import jakarta.persistence.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalProvisioningModelTest {
    static void field(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static void lifecycle(Object target, Class<? extends Annotation> annotation) throws Exception {
        Method m = Arrays.stream(target.getClass().getDeclaredMethods()).filter(x -> x.isAnnotationPresent(annotation)).findFirst().orElseThrow();
        m.setAccessible(true);
        try { m.invoke(target); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }
    static LocalClassroom classroom() {
        return new LocalClassroom(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " aula_1 ", " Aula ");
    }
    static String hash() { return "ab".repeat(32); }

    @Test void strictUuidHashAndCodeRulesRejectNoncanonicalInputWithoutSilentRepair() {
        var converter = new CanonicalUuidConverter(); UUID id = UUID.randomUUID();
        assertEquals(id, converter.convertToEntityAttribute(id.toString()));
        assertEquals(id.toString(), converter.convertToDatabaseColumn(id));
        assertNull(converter.convertToEntityAttribute(null));
        for (String value : List.of("1-1-1-1-1", id.toString().toUpperCase(Locale.ROOT), id+" ", "00000000-0000-0000-0000-000000000000"))
            assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute(value));
        for (String value : List.of("", "a".repeat(63), "a".repeat(65), "AB".repeat(32), "z".repeat(64)))
            assertThrows(IllegalArgumentException.class, () -> ProvisioningValidation.fingerprint(value));
        for (String code : List.of("ß", "ı", "A-B", "x".repeat(81)))
            assertThrows(IllegalArgumentException.class, () -> ProvisioningValidation.code(code));
        assertThrows(IllegalArgumentException.class, () -> ProvisioningValidation.name("\u00a0\u0085"));
        assertEquals(1, ProvisioningValidation.contractVersion(1));
        assertThrows(IllegalArgumentException.class, () -> ProvisioningValidation.contractVersion(2));
    }

    @Test void localClassroomKeepsPortalIdentifiersAndNormalizesOnlyCodeAndInitialName() throws Exception {
        var value = classroom();
        assertEquals("AULA_1", value.getCode()); assertEquals("Aula", value.getName());
        UUID portal = value.getPortalInstanceId(), aula = value.getPortalClassroomId(), academy = value.getTechnicalAcademyId();
        lifecycle(value, PrePersist.class); lifecycle(value, PostPersist.class);
        assertEquals(portal, value.getPortalInstanceId()); assertEquals(aula, value.getPortalClassroomId());
        assertEquals(academy, value.getTechnicalAcademyId());
        assertEquals(value.getCreatedAt(), value.getPreparedAt()); assertEquals(value.getCreatedAt(), value.getUpdatedAt());
        assertEquals(0, value.getVersion()); assertNull(value.getId());
        assertThrows(IllegalArgumentException.class, () -> new LocalClassroom(null, aula, academy, "A", "Aula"));
        assertThrows(IllegalArgumentException.class, () -> new LocalClassroom(portal, aula, academy, "A", "x".repeat(191)));
    }

    @Test void persistedLocalIdentityAndInitialNameCannotBeChanged() throws Exception {
        for (String name : List.of("portalInstanceId", "portalClassroomId", "technicalAcademyId", "code", "name")) {
            var value = classroom(); lifecycle(value, PrePersist.class); lifecycle(value, PostLoad.class);
            field(value, name, name.equals("code") ? "OTHER" : name.equals("name") ? "Other" : UUID.randomUUID());
            assertThrows(IllegalStateException.class, () -> lifecycle(value, PreUpdate.class));
        }
        var negative = classroom(); field(negative, "version", -1L);
        assertThrows(IllegalStateException.class, () -> lifecycle(negative, PrePersist.class));
    }

    @Test void successfulReceiptRequiresPersistedMatchingContextAndIsImmutable() throws Exception {
        var value = classroom(); UUID op = UUID.randomUUID();
        var receipt = ClassroomProvisioningReceipt.ready(op, hash(), value);
        assertThrows(IllegalStateException.class, () -> lifecycle(receipt, PrePersist.class));
        field(value, "id", 10L); lifecycle(value, PrePersist.class);
        lifecycle(receipt, PrePersist.class);
        assertEquals(op, receipt.getOperationId()); assertEquals(ProvisioningResult.READY, receipt.getResult());
        assertSame(value, receipt.getLocalClassroom()); assertNull(receipt.getErrorCode()); assertNotNull(receipt.getCompletedAt());
        assertThrows(IllegalStateException.class, () -> lifecycle(receipt, PreUpdate.class));
        field(receipt, "portalClassroomId", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> lifecycle(receipt, PrePersist.class));
    }

    @Test void definitiveFailureHasNoClassroomAndTimeoutIsNeverAnAcceptedDefinitiveResult() throws Exception {
        UUID portal = UUID.randomUUID(), aula = UUID.randomUUID(), academy = UUID.randomUUID(), op = UUID.randomUUID();
        var receipt = ClassroomProvisioningReceipt.failed(portal, op, aula, academy, hash(), ProvisioningErrorCode.PREPARATION_REJECTED);
        lifecycle(receipt, PrePersist.class);
        assertEquals(ProvisioningResult.FAILED, receipt.getResult()); assertNull(receipt.getLocalClassroom());
        assertEquals(ProvisioningErrorCode.PREPARATION_REJECTED, receipt.getErrorCode());
        for (var error : ProvisioningErrorCode.values())
            if (error != ProvisioningErrorCode.PREPARATION_REJECTED)
                assertThrows(IllegalArgumentException.class, () -> ClassroomProvisioningReceipt.failed(portal, op, aula, academy, hash(), error));
        assertThrows(IllegalArgumentException.class, () -> ClassroomProvisioningReceipt.ready(op, hash(), null));
    }

    @Test void localIdentityAndReceiptsUseUtcWithoutAnyJdbcOrConfiguration() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            LocalDateTime before = LocalDateTime.now(Clock.systemUTC()).minusSeconds(1);
            var value = classroom(); lifecycle(value, PrePersist.class); field(value, "id", 1L);
            var receipt = ClassroomProvisioningReceipt.ready(UUID.randomUUID(), hash(), value); lifecycle(receipt, PrePersist.class);
            assertFalse(value.getPreparedAt().isBefore(before)); assertFalse(receipt.getCompletedAt().isBefore(before));
            assertTrue(receipt.getCompletedAt().isBefore(LocalDateTime.now(Clock.systemUTC()).plusSeconds(1)));
            // First response and replay must use the exact same DATETIME(6) precision.
            assertEquals(0, value.getPreparedAt().getNano() % 1000);
            assertEquals(0, receipt.getCompletedAt().getNano() % 1000);
        } finally { TimeZone.setDefault(previous); }
    }
}
