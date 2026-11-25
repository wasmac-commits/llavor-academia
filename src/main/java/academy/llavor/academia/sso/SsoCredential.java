package academy.llavor.academia.sso;
import java.util.Arrays;
import java.util.function.Function;
/** Short-lived decrypted credential, never Serializable, cached, exposed as a bean property or put in session. */
public final class SsoCredential implements AutoCloseable {
    private char[] value;
    SsoCredential(String value){this.value=SsoValidation.canonical32(value).toCharArray();}
    public <T>T use(Function<String,T> action){
        if(value==null)throw new SsoClientFailure(SsoClientFailure.Code.CREDENTIAL_UNAVAILABLE);
        return action.apply(new String(value));
    }
    public void close(){if(value!=null){Arrays.fill(value,'\0');value=null;}}
    public String toString(){return "SsoCredential[redacted]";}
}
