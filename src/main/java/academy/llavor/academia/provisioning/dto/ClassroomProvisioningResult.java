package academy.llavor.academia.provisioning.dto;

import academy.llavor.academia.provisioning.entity.ClassroomProvisioningReceipt;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@jakarta.json.bind.annotation.JsonbNillable
public final class ClassroomProvisioningResult {
    private final int contractVersion = 1;
    private final String portalInstanceId, academyId, classroomId, operationId, result, completedAt, preparedAt, errorCode;
    public ClassroomProvisioningResult(ClassroomProvisioningReceipt receipt) {
        portalInstanceId = receipt.getPortalInstanceId().toString(); academyId = receipt.getTechnicalAcademyId().toString();
        classroomId = receipt.getPortalClassroomId().toString(); operationId = receipt.getOperationId().toString();
        result = receipt.getResult().name();
        completedAt = receipt.getCompletedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        preparedAt = receipt.getLocalClassroom() == null ? null : receipt.getLocalClassroom().getPreparedAt()
            .atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        errorCode = receipt.getErrorCode() == null ? null : receipt.getErrorCode().name();
    }
    public int getContractVersion() { return contractVersion; }
    public String getPortalInstanceId() { return portalInstanceId; }
    public String getAcademyId() { return academyId; }
    public String getClassroomId() { return classroomId; }
    public String getOperationId() { return operationId; }
    public String getResult() { return result; }
    public String getCompletedAt() { return completedAt; }
    public String getPreparedAt() { return preparedAt; }
    public String getErrorCode() { return errorCode; }
}
