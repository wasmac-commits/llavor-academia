package academy.llavor.academia.provisioning.dto;

@jakarta.json.bind.annotation.JsonbNillable
public final class ProvisioningError {
    private final String errorCode;
    public ProvisioningError(String errorCode) { this.errorCode = errorCode; }
    public int getContractVersion() { return 1; }
    public String getOperationId() { return null; }
    public String getErrorCode() { return errorCode; }
}
