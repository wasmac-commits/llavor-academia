package academy.llavor.academia.parameter.security;
import academy.llavor.academia.parameter.service.ParameterAccessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Token lives only for this invocation. No AES, plaintext storage, logs or receiver changes. */
@ApplicationScoped @Transactional(Transactional.TxType.NOT_SUPPORTED)
public class LocalCredentialVerifier {
 private AcademyVerifierParameterReader parameters;
 protected LocalCredentialVerifier(){}
 @Inject public LocalCredentialVerifier(AcademyVerifierParameterReader parameters){this.parameters=parameters;}
 public boolean matches(Set<String> acceptedKeyIds,String keyId,String token){
  if(acceptedKeyIds==null||acceptedKeyIds.isEmpty()||acceptedKeyIds.size()>4||acceptedKeyIds.stream().anyMatch(id->id==null||!id.matches("[A-Za-z0-9_-]{1,32}"))||keyId==null||!keyId.matches("[A-Za-z0-9_-]{1,32}")||!acceptedKeyIds.contains(keyId))return false;
  byte[] decoded=null,text=null,actual=null;
  try{
   if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return false;
   decoded=Base64.getUrlDecoder().decode(token);
   if(decoded.length!=32||!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token))return false;
   text=token.getBytes(StandardCharsets.UTF_8);actual=MessageDigest.getInstance("SHA-256").digest(text);
   final byte[] digest=actual;
   return parameters.read(keyId).use(verifier->{
    if(verifier==null||!verifier.matches("[0-9a-f]{64}"))throw new CredentialVerificationException();
    byte[] expected=HexFormat.of().parseHex(verifier);try{return MessageDigest.isEqual(digest,expected);}finally{Arrays.fill(expected,(byte)0);}
   });
  }catch(ParameterAccessException failure){
   if(failure.getReason()==ParameterAccessException.Reason.NOT_FOUND||failure.getReason()==ParameterAccessException.Reason.FORBIDDEN)return false;
   throw new CredentialVerificationException(failure.getReason()==ParameterAccessException.Reason.STORAGE_FAILURE);
  }catch(CredentialVerificationException safe){throw safe;}catch(Exception ignored){throw new CredentialVerificationException();}
  finally{if(decoded!=null)Arrays.fill(decoded,(byte)0);if(text!=null)Arrays.fill(text,(byte)0);if(actual!=null)Arrays.fill(actual,(byte)0);}
 }
}
