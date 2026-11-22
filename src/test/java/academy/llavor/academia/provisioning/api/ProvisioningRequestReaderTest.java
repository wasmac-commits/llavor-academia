package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.ReceiverFixture;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.ws.rs.core.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProvisioningRequestReaderTest {
    private static ClassroomProvisioningRequest read(String json) {
        return new ClassroomProvisioningRequestReader().readFrom(ClassroomProvisioningRequest.class, ClassroomProvisioningRequest.class,
            new Annotation[0], MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(),
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    private static String json(ReceiverFixture f) {
        return "{\"contractVersion\":1,\"portalInstanceId\":\""+f.portal+"\",\"academyCode\":\"ACADEMIA_001\","
            +"\"classroomCode\":\"AULA_001\",\"classroomName\":\"Aula inicial\"}";
    }
    @Test void realJsonParserAcceptsV1AndPropertyOrderingDoesNotChangeFingerprint() {
        var f = new ReceiverFixture();
        var first = read(json(f));
        var second = read("{\"classroomName\":\"Aula inicial\",\"classroomCode\":\"AULA_001\",\"academyCode\":\"ACADEMIA_001\","
            +"\"portalInstanceId\":\""+f.portal+"\",\"contractVersion\":1}");
        var a = ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), first);
        var b = ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), second);
        assertTrue(a.fingerprint().equals(b.fingerprint()));
    }
    @Test void duplicateUnknownMissingWrongTypedAndTrailingPayloadsAreRejected() {
        var f = new ReceiverFixture(); String valid = json(f);
        for (String invalid : List.of(
                valid.replace("\"contractVersion\":1", "\"contractVersion\":1,\"contractVersion\":1"),
                valid.replace("\"contractVersion\":1", "\"contractVersion\":\"1\""),
                valid.replace("\"contractVersion\":1", "\"contractVersion\":1.5"),
                valid.replace("\"contractVersion\":1", "\"contractVersion\":9999999999999999999"),
                valid.replace("\"contractVersion\":1,", ""),
                valid.replace("\"classroomName\":\"Aula inicial\"", "\"classroomName\":null"),
                valid.replace("\"classroomName\":\"Aula inicial\"", "\"classroomName\":{}"),
                valid.substring(0, valid.length()-1)+",\"active\":true}", valid+"{}", "[]", "{")) {
            var failure = assertThrows(ProvisioningFailure.class, () -> read(invalid));
            assertEquals(ProvisioningFailure.Code.INVALID_REQUEST, failure.getCode()); assertNull(failure.getCause());
        }
    }
    @Test void streamAndContentLimitsApplyBeforeAnyBusinessServiceOrDatabase() {
        var f = new ReceiverFixture();
        var tooLarge = json(f).replace("Aula inicial", "a".repeat(20000));
        assertThrows(ProvisioningFailure.class, () -> read(tooLarge));
        var request = read(json(f).replace("Aula inicial", "a".repeat(191)));
        assertThrows(ProvisioningFailure.class, () -> ProvisioningCommand.from(f.principal, f.academy.toString(),
            f.classroom.toString(), f.operation.toString(), request));
    }
}
