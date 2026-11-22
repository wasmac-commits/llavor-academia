package academy.llavor.academia.provisioning.service;

/** No original cause, input, credential or database message is retained. */
public final class ProvisioningFailure extends RuntimeException {
    public enum Code {
        INVALID_REQUEST(400), UNSUPPORTED_CONTRACT_VERSION(400), UNAUTHORIZED(401), FORBIDDEN(403),
        ACADEMY_IDENTITY_MISMATCH(403), PORTAL_IDENTITY_MISMATCH(403),
        OPERATION_CONTENT_CONFLICT(409), CLASSROOM_IDENTITY_CONFLICT(409),
        NOT_FOUND(404), PROVISIONING_DISABLED(503), INVALID_CONFIGURATION(503), TEMPORARILY_UNAVAILABLE(503);
        private final int status;
        Code(int status) { this.status = status; }
        public int status() { return status; }
    }
    private final Code code;
    public ProvisioningFailure(Code code) { super(code.name()); this.code = code; }
    public Code getCode() { return code; }
}
