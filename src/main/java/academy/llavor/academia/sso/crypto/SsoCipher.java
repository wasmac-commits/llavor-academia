package academy.llavor.academia.sso.crypto;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
/** One pure-Java implementation shared by Academia and the JDBC installer at build time. */
public final class SsoCipher {
    public static final String MASTER_ID="M01";
    @FunctionalInterface public interface Keys { byte[] load(); }
    public static final class Failure extends RuntimeException {
        public Failure(){super("SSO_PROTECTED_CREDENTIAL_UNAVAILABLE",null,false,false);}
    }
    public record Context(UUID portalId,UUID academyId,String academyCode,String keyId,
            UUID operationId,int credentialVersion,Instant createdAt) {
        public Context {
            if(portalId==null||academyId==null||operationId==null)throw new Failure();
            for(UUID id:List.of(portalId,academyId,operationId))if(id.equals(new UUID(0,0)))throw new Failure();
            if(academyCode==null||!academyCode.matches("[A-Z0-9_]{1,80}")||!"S01".equals(keyId)
                    ||credentialVersion!=1||createdAt==null||createdAt.getNano()%1000!=0
                    ||createdAt.atOffset(java.time.ZoneOffset.UTC).getYear()<1000
                    ||createdAt.atOffset(java.time.ZoneOffset.UTC).getYear()>9999)throw new Failure();
        }
    }
    private final Keys keys;private final SecureRandom random;
    public SsoCipher(Keys keys){this(keys,new SecureRandom());}
    public SsoCipher(Keys keys,SecureRandom random){this.keys=Objects.requireNonNull(keys);this.random=Objects.requireNonNull(random);}
    public String encrypt(Context context,char[] token){
        byte[] key=null,clear=null,nonce=new byte[12],encrypted=null;
        try{
            canonicalToken(token);clear=new String(token).getBytes(StandardCharsets.US_ASCII);key=key();random.nextBytes(nonce);
            encrypted=cipher(Cipher.ENCRYPT_MODE,key,nonce,context).doFinal(clear);
            return "v1."+MASTER_ID+"."+encode(nonce)+"."+encode(encrypted);
        }catch(Exception e){throw new Failure();}
        finally{wipe(key);wipe(clear);wipe(nonce);wipe(encrypted);}
    }
    public char[] decrypt(Context context,String envelope){
        byte[] key=null,clear=null,nonce=null,encrypted=null;
        try{
            var parts=envelope.split("[.]",-1);
            if(parts.length!=4||!parts[0].equals("v1")||!parts[1].equals(MASTER_ID))throw new Failure();
            nonce=decode(parts[2],12);encrypted=decode(parts[3],59);key=key();
            clear=cipher(Cipher.DECRYPT_MODE,key,nonce,context).doFinal(encrypted);
            if(clear.length!=43)throw new Failure();
            char[] token=new String(clear,StandardCharsets.US_ASCII).toCharArray();
            try{canonicalToken(token);return token;}catch(RuntimeException e){Arrays.fill(token,'\0');throw e;}
        }catch(Exception e){throw new Failure();}
        finally{wipe(key);wipe(clear);wipe(nonce);wipe(encrypted);}
    }
    public static String digest(char[] token){
        byte[] text=null;
        try{canonicalToken(token);text=new String(token).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text));
        }catch(Exception e){throw new Failure();}finally{wipe(text);}
    }
    public static byte[] master(String text){try{return decode(text,32);}catch(Exception e){throw new Failure();}}
    private static void canonicalToken(char[] token){
        if(token==null)throw new Failure();byte[] value=null;
        try{value=decode(new String(token),32);}finally{wipe(value);}
    }
    private byte[] key(){byte[] key=keys.load();if(key==null||key.length!=32){wipe(key);throw new Failure();}return key;}
    private static Cipher cipher(int mode,byte[] key,byte[] nonce,Context context)throws Exception{
        if(context==null)throw new Failure();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        for(String field:List.of("llavor.academia.sso.credential","v1",MASTER_ID,context.portalId().toString(),
                context.academyId().toString(),context.academyCode(),context.keyId(),context.operationId().toString(),
                Integer.toString(context.credentialVersion()),context.createdAt().toString())){
            byte[] value=field.getBytes(StandardCharsets.UTF_8);out.writeInt(value.length);out.write(value);
        }
        out.flush();c.updateAAD(bytes.toByteArray());return c;
    }
    private static byte[] decode(String text,int length){
        if(text==null||!text.matches("[A-Za-z0-9_-]+"))throw new Failure();
        byte[] value=Base64.getUrlDecoder().decode(text);
        if(value.length!=length||!encode(value).equals(text)){wipe(value);throw new Failure();}return value;
    }
    private static String encode(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static void wipe(byte[] b){if(b!=null)Arrays.fill(b,(byte)0);}
}
