package academy.llavor.academia.provisioning.dto;

/** JSON-B input. No credentials, identity defaults or administrative/provisioning state setters. */
public class ClassroomProvisioningRequest {
    private Integer contractVersion;
    private String portalInstanceId;
    private String academyCode;
    private String classroomCode;
    private String classroomName;
    public ClassroomProvisioningRequest() {}
    public Integer getContractVersion() { return contractVersion; }
    public void setContractVersion(Integer value) { contractVersion = value; }
    public String getPortalInstanceId() { return portalInstanceId; }
    public void setPortalInstanceId(String value) { portalInstanceId = value; }
    public String getAcademyCode() { return academyCode; }
    public void setAcademyCode(String value) { academyCode = value; }
    public String getClassroomCode() { return classroomCode; }
    public void setClassroomCode(String value) { classroomCode = value; }
    public String getClassroomName() { return classroomName; }
    public void setClassroomName(String value) { classroomName = value; }
}
