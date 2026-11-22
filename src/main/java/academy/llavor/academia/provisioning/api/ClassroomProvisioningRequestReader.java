package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.dto.ClassroomProvisioningRequest;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import jakarta.json.Json;
import jakarta.json.stream.JsonParser;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.INVALID_REQUEST;

/** Strict flat v1 input: no duplicate/unknown fields, coercion, trailing JSON or unbounded body. */
@Provider @Consumes(MediaType.APPLICATION_JSON)
public class ClassroomProvisioningRequestReader implements MessageBodyReader<ClassroomProvisioningRequest> {
    private static final Set<String> FIELDS = Set.of("contractVersion", "portalInstanceId", "academyCode", "classroomCode", "classroomName");
    @Override public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type == ClassroomProvisioningRequest.class;
    }
    @Override public ClassroomProvisioningRequest readFrom(Class<ClassroomProvisioningRequest> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> headers, InputStream stream) {
        try (JsonParser parser = Json.createParser(new ProvisioningAuthenticationFilter.LimitedBody(stream))) {
            if (!parser.hasNext() || parser.next() != JsonParser.Event.START_OBJECT) throw invalid();
            Set<String> seen = new HashSet<>();
            ClassroomProvisioningRequest request = new ClassroomProvisioningRequest();
            boolean ended = false;
            while (parser.hasNext()) {
                JsonParser.Event event = parser.next();
                if (event == JsonParser.Event.END_OBJECT) { ended = true; break; }
                if (event != JsonParser.Event.KEY_NAME) throw invalid();
                String key = parser.getString();
                if (!FIELDS.contains(key) || !seen.add(key) || !parser.hasNext()) throw invalid();
                JsonParser.Event value = parser.next();
                if (key.equals("contractVersion")) {
                    if (value != JsonParser.Event.VALUE_NUMBER || !parser.isIntegralNumber()) throw invalid();
                    request.setContractVersion(parser.getBigDecimal().intValueExact());
                } else {
                    if (value != JsonParser.Event.VALUE_STRING) throw invalid();
                    String text = parser.getString();
                    switch (key) {
                        case "portalInstanceId" -> request.setPortalInstanceId(text);
                        case "academyCode" -> request.setAcademyCode(text);
                        case "classroomCode" -> request.setClassroomCode(text);
                        case "classroomName" -> request.setClassroomName(text);
                        default -> throw invalid();
                    }
                }
            }
            if (!ended || !seen.equals(FIELDS) || parser.hasNext()) throw invalid();
            return request;
        } catch (ProvisioningFailure failure) { throw failure; }
        catch (RuntimeException ignored) { throw invalid(); }
    }
    private static ProvisioningFailure invalid() { return new ProvisioningFailure(INVALID_REQUEST); }
}
