package academy.llavor.academia.provisioning.dto;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class ClassroomIdentityResult {
    private final String portalInstanceId, academyId, classroomId, classroomCode, classroomName, preparedAt;
    public ClassroomIdentityResult(LocalClassroom classroom) {
        portalInstanceId = classroom.getPortalInstanceId().toString(); academyId = classroom.getTechnicalAcademyId().toString();
        classroomId = classroom.getPortalClassroomId().toString(); classroomCode = classroom.getCode(); classroomName = classroom.getName();
        preparedAt = classroom.getPreparedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    public int getContractVersion() { return 1; }
    public String getResult() { return "READY"; }
    public String getPortalInstanceId() { return portalInstanceId; }
    public String getAcademyId() { return academyId; }
    public String getClassroomId() { return classroomId; }
    public String getClassroomCode() { return classroomCode; }
    public String getClassroomName() { return classroomName; }
    public String getPreparedAt() { return preparedAt; }
}
