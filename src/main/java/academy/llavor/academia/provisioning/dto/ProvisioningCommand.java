package academy.llavor.academia.provisioning.dto;

import academy.llavor.academia.provisioning.config.AcademiaIdentity;
import academy.llavor.academia.provisioning.security.ProvisioningPrincipal;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;

/** Immutable, normalized business request, independent of JSON property order and key rotation. */
public record ProvisioningCommand(UUID portalId, UUID academyId, UUID classroomId, UUID operationId,
                                  String academyCode, String classroomCode, String classroomName) {
    public ProvisioningCommand {
        ProvisioningValidation.uuid(portalId); ProvisioningValidation.uuid(academyId);
        ProvisioningValidation.uuid(classroomId); ProvisioningValidation.uuid(operationId);
        if (!Objects.equals(academyCode, ProvisioningValidation.code(academyCode))
                || !Objects.equals(classroomCode, ProvisioningValidation.code(classroomCode)))
            throw new IllegalArgumentException("Los codigos deben estar normalizados.");
        classroomName = ProvisioningValidation.name(classroomName);
    }

    public static ProvisioningCommand from(ProvisioningPrincipal principal, String academy, String classroom,
                                           String operation, ClassroomProvisioningRequest request) {
        try {
            if (request == null || request.getContractVersion() == null) throw new ProvisioningFailure(INVALID_REQUEST);
            if (request.getContractVersion() != 1) throw new ProvisioningFailure(UNSUPPORTED_CONTRACT_VERSION);
            UUID academyId = ProvisioningValidation.parseUuid(academy), classroomId = ProvisioningValidation.parseUuid(classroom);
            UUID operationId = ProvisioningValidation.parseUuid(operation);
            UUID portalId = ProvisioningValidation.parseUuid(request.getPortalInstanceId());
            if (!principal.academyId().equals(academyId) || !principal.academyCode().equals(request.getAcademyCode()))
                throw new ProvisioningFailure(ACADEMY_IDENTITY_MISMATCH);
            if (!principal.portalId().equals(portalId)) throw new ProvisioningFailure(PORTAL_IDENTITY_MISMATCH);
            return new ProvisioningCommand(portalId, academyId, classroomId, operationId,
                request.getAcademyCode(), request.getClassroomCode(), request.getClassroomName());
        } catch (ProvisioningFailure failure) { throw failure; }
        catch (IllegalArgumentException ignored) { throw new ProvisioningFailure(INVALID_REQUEST); }
    }

    /** Canonical format v1: UTF-8 fields, each prefixed by its 4-byte unsigned-compatible length. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of("1", portalId.toString(), academyId.toString(), classroomId.toString(),
                    operationId.toString(), academyCode, classroomCode, classroomName)) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ignored) { throw new ProvisioningFailure(INVALID_CONFIGURATION); }
    }
}
