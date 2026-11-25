package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.crypto.SsoCipher;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@ApplicationScoped
public class BrowserTokens {
    private final SecureRandom entropy;
    public BrowserTokens(){this(new SecureRandom());}
    BrowserTokens(SecureRandom entropy){this.entropy=entropy;}
    public String next(){byte[] b=new byte[32];try{entropy.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}finally{Arrays.fill(b,(byte)0);}}
    public static String hash(String token){try{return SsoCipher.digest(token.toCharArray());}catch(RuntimeException e){throw new BrowserFailure("INVALID_BINDING");}}
    public static boolean same(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    public static UUID uuid(String text){try{UUID id=UUID.fromString(text);if(!id.toString().equals(text)||id.equals(new UUID(0,0)))throw new IllegalArgumentException();return id;}catch(RuntimeException e){throw new BrowserFailure("INVALID_REQUEST");}}
}
