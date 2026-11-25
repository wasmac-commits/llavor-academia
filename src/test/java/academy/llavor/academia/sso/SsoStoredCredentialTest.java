package academy.llavor.academia.sso;
import academy.llavor.academia.sso.crypto.*;
import academy.llavor.academia.provisioning.config.ExpectedInstallationIdentity;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SsoStoredCredentialTest {
    final UUID a=SsoConfigurationTest.A,p=SsoConfigurationTest.P,operation=UUID.fromString("44444444-4444-4444-8444-444444444444");
    final Instant now=Instant.parse("2026-01-01T12:00:00Z");
    final byte[] master=new byte[32];final String secret="A".repeat(43);
    SsoStoredCredential row;SsoCredentialRepository store;AcademyInstanceRepository identities;ExpectedInstallationIdentity pin;
    SsoCredentialProvider provider;
    @BeforeEach void setup()throws Exception{
        row=new SsoStoredCredential();field("academyId",a);field("portalId",p);field("academyCode","TEST");field("keyId","S01");
        field("operationId",operation);field("credentialVersion",1);field("masterKeyId","M01");
        field("createdAt",LocalDateTime.ofInstant(now,ZoneOffset.UTC));field("updatedAt",LocalDateTime.ofInstant(now,ZoneOffset.UTC));
        field("state",SsoStoredCredential.State.ENABLED);
        field("encryptedSecret",new SsoCipher(()->master.clone()).encrypt(row.context(),secret.toCharArray()));
        store=mock(SsoCredentialRepository.class);identities=mock(AcademyInstanceRepository.class);pin=mock(ExpectedInstallationIdentity.class);
        when(store.find()).thenReturn(Optional.of(row));when(identities.find()).thenReturn(Optional.of(new AcademyInstance(a,"TEST",p)));when(pin.require(a)).thenReturn(a);
        provider=new SsoCredentialProvider(pin,identities,store,()->master.clone());
    }
    void field(String name,Object value)throws Exception{var f=SsoStoredCredential.class.getDeclaredField(name);f.setAccessible(true);f.set(row,value);}
    @Test void onlyEnabledCoherentProtectedCredentialCanBeDecrypted(){
        try(var credential=provider.load(a,"S01")){assertEquals(secret,credential.use(x->x));assertFalse(credential.toString().contains(secret));}
        assertFalse(row.toString().contains(secret));assertFalse(row.toString().contains(row.encryptedSecret()));
    }
    @Test void disabledStoreAndMissingRowFailClosed()throws Exception{
        field("state",SsoStoredCredential.State.DISABLED);assertThrows(SsoClientFailure.class,()->provider.load(a,"S01"));
        when(store.find()).thenReturn(Optional.empty());assertThrows(SsoClientFailure.class,()->provider.load(a,"S01"));
    }
    @Test void wrongIdentityAndKeyNeverAccessSecret(){
        assertThrows(SsoClientFailure.class,()->provider.load(p,"S01"));assertThrows(SsoClientFailure.class,()->provider.load(a,"K01"));verifyNoInteractions(store);
    }
    @Test void corruptionMetadataOrMissingMasterAreSafe()throws Exception{
        String cipher=row.encryptedSecret();field("encryptedSecret",cipher.substring(0,cipher.length()-1)+"B");
        var error=assertThrows(SsoClientFailure.class,()->provider.load(a,"S01"));assertNull(error.getCause());assertEquals(0,error.getStackTrace().length);
        field("encryptedSecret",cipher);field("operationId",a);assertThrows(SsoClientFailure.class,()->provider.load(a,"S01"));
        field("operationId",operation);provider=new SsoCredentialProvider(pin,identities,store,()->{throw new IllegalStateException("canary");});
        assertFalse(assertThrows(SsoClientFailure.class,()->provider.load(a,"S01")).toString().contains("canary"));
    }
    @Test void sqlJpaAndIsolationContract()throws Exception{
        String sql=Files.readString(Path.of("database/migrations/V004__sso_credentials.sql"));
        assertTrue(sql.contains("REFERENCES academy_instance (id,instance_id,portal_instance_id,academy_code)"));
        assertTrue(sql.contains("DEFAULT 'DISABLED'"));assertTrue(sql.contains("UNIQUE KEY uk_sso_credential_identity_key"));
        assertTrue(sql.contains("credential_version=1"));assertTrue(sql.contains("key_id='S01'"));assertTrue(sql.contains("version>=0"));
        for(var f:SsoStoredCredential.class.getDeclaredFields()){
            var column=f.getAnnotation(Column.class);if(column!=null)assertTrue(sql.contains((column.name().isEmpty()?f.getName():column.name())+" "),f.getName());
        }
        assertNotNull(SsoStoredCredential.class.getDeclaredField("version").getAnnotation(Version.class));
        String providerSource=Files.readString(Path.of("src/main/java/academy/llavor/academia/sso/SsoCredentialProvider.java"));
        assertFalse(providerSource.contains("Password Alias"));assertFalse(providerSource.contains("ConfigProvider"));assertFalse(providerSource.contains("asadmin"));
    }
}
