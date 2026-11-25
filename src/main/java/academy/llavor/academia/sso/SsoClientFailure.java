package academy.llavor.academia.sso;
/** Safe failure: no input/body/credential/exception cause is retained or logged. */
public final class SsoClientFailure extends RuntimeException {
    public enum Code { DISABLED, INVALID_REQUEST, INVALID_CONFIGURATION, IDENTITY_MISMATCH,
        CREDENTIAL_UNAVAILABLE, AUTHENTICATION_REJECTED, ACCESS_DENIED, TICKET_UNAVAILABLE,
        REMOTE_UNAVAILABLE, INVALID_RESPONSE, REDIRECT_REJECTED, TIMEOUT, TLS_REJECTED }
    private final Code code;private final boolean consumptionMayHaveCommitted;
    public SsoClientFailure(Code code){this(code,false);}
    public SsoClientFailure(Code code,boolean uncertain){super("SSO: "+code.name(),null,false,false);this.code=code;this.consumptionMayHaveCommitted=uncertain;}
    public Code getCode(){return code;}
    public boolean isConsumptionMayHaveCommitted(){return consumptionMayHaveCommitted;}
}
