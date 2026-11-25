package academy.llavor.academia.sso.browser;
/** Only codes defined inside this package enter this exception. Never wrap request/driver messages. */
public final class BrowserFailure extends RuntimeException {
    private final String code;
    BrowserFailure(String code){super("SSO_BROWSER_REJECTED",null,false,false);this.code=code;}
    public String code(){return code;}
}
