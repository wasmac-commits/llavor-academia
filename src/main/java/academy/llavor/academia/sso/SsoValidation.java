package academy.llavor.academia.sso;
import java.util.*;
final class SsoValidation {
    private SsoValidation(){}
    static UUID uuid(UUID value){if(value==null||value.equals(new UUID(0,0)))throw invalid();return value;}
    static UUID uuid(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw invalid();return uuid(id);}catch(RuntimeException e){throw invalid();}}
    static String canonical32(String value){
        if(value==null||!value.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        byte[] bytes=null;
        try{bytes=Base64.getUrlDecoder().decode(value);if(bytes.length!=32||!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value))throw invalid();return value;}
        catch(IllegalArgumentException e){throw invalid();}finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    static String binding(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw invalid();return value;}
    private static SsoClientFailure invalid(){return new SsoClientFailure(SsoClientFailure.Code.INVALID_REQUEST);}
}
