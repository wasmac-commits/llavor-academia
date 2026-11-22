package academy.llavor.academia.parameter.security;
import academy.llavor.academia.parameter.service.ParameterAccessException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LocalCredentialVerifierTest {
 AcademyVerifierParameterReader reader=mock(AcademyVerifierParameterReader.class);LocalCredentialVerifier service=new LocalCredentialVerifier(reader);
 String token,verifier;
 @BeforeEach void setup()throws Exception{
  byte[] random=new byte[32];new SecureRandom().nextBytes(random);token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);Arrays.fill(random,(byte)0);
  verifier=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));when(reader.read("K01")).thenReturn(new SensitiveParameterValue(verifier));
 }
 @Test void acceptsOnlyTheVerifierOfCanonicalUtf8TokenText()throws Exception{
  assertTrue(service.matches(Set.of("K01"),"K01",token));
  byte[] other=new byte[32];new SecureRandom().nextBytes(other);assertFalse(service.matches(Set.of("K01"),"K01",Base64.getUrlEncoder().withoutPadding().encodeToString(other)));
  String decodedHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(token)));when(reader.read("K01")).thenReturn(new SensitiveParameterValue(decodedHash));assertFalse(service.matches(Set.of("K01"),"K01",token));
 }
 @Test void keyVersionAllowListAndMalformedTokensAreRejectedBeforeReading(){
  assertFalse(service.matches(Set.of("K02"),"K01",token));assertFalse(service.matches(Set.of(),"K01",token));
  for(String invalid:List.of("",token+"="," "+token,token.substring(1)))assertFalse(service.matches(Set.of("K01"),"K01",invalid));
  verifyNoInteractions(reader);
 }
 @Test void absentOrInactiveKeysRejectAuthenticationAndStorageFailuresAreSafe(){
  doThrow(new ParameterAccessException(ParameterAccessException.Reason.NOT_FOUND)).when(reader).read("K01");assertFalse(service.matches(Set.of("K01"),"K01",token));
  doThrow(new ParameterAccessException(ParameterAccessException.Reason.FORBIDDEN)).when(reader).read("K01");assertFalse(service.matches(Set.of("K01"),"K01",token));
  doThrow(new IllegalStateException(token)).when(reader).read("K01");var error=assertThrows(CredentialVerificationException.class,()->service.matches(Set.of("K01"),"K01",token));assertNull(error.getCause());assertFalse(error.getMessage().contains(token));assertFalse(error.getMessage().contains(verifier));
 }
 @Test void malformedVerifiersFailWithoutReturningMaterial(){
  when(reader.read("K01")).thenReturn(new SensitiveParameterValue(verifier.toUpperCase(Locale.ROOT)));var error=assertThrows(CredentialVerificationException.class,()->service.matches(Set.of("K01"),"K01",token));assertNull(error.getCause());assertFalse(error.getMessage().contains(verifier));
 }
}
