package academy.llavor.academia.credential;
import academy.llavor.academia.credential.entity.*;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.credential.validation.CredentialMetadata;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CredentialInstallationModelTest {
    static final Instant NOW=Instant.parse("2026-11-22T10:00:00.123456789Z");
    static final String HASH="a".repeat(64),FINGERPRINT="b".repeat(64);
    static CredentialInstallationReceipt receipt(){return new CredentialInstallationReceipt(UUID.randomUUID(),1,new AcademyInstance(UUID.randomUUID(),"ACADEMIA_TEST",UUID.randomUUID()),"K01",HASH,FINGERPRINT,NOW,NOW.plusSeconds(1));}
    @Test void receiptCopiesPermanentIdentityAndStoresUtcMetadataOnly(){
        var r=receipt();assertEquals(r.getAcademyInstance().getInstanceId(),r.getAcademyInstanceId());assertEquals(r.getAcademyInstance().getPortalInstanceId(),r.getPortalInstanceId());assertEquals("ACADEMIA_TEST",r.getAcademyCode());
        assertEquals(LocalDateTime.of(2026,11,22,10,0,0,123456000),r.getInstalledAt());assertEquals(0,r.getVersion());assertEquals(CredentialInstallationResult.INSTALLED,r.getResult());
    }
    @Test void reinstallationPreservesIdentityHashesAndFirstInstallationDate(){
        var r=receipt();var installed=r.getInstalledAt();var operation=r.getOperationId();
        r.confirmExisting(HASH,FINGERPRINT,NOW.plusSeconds(2));r.confirmExisting(HASH,FINGERPRINT,NOW.plusSeconds(2));
        assertEquals(CredentialInstallationResult.ALREADY_INSTALLED,r.getResult());assertEquals(installed,r.getInstalledAt());assertEquals(operation,r.getOperationId());assertEquals(1,r.getPackageRevision());
    }
    @Test void contradictoryPackageOrConfigurationCannotOverwriteReceipt(){
        var r=receipt();assertThrows(IllegalStateException.class,()->r.confirmExisting(FINGERPRINT,FINGERPRINT,NOW.plusSeconds(2)));
        assertThrows(IllegalStateException.class,()->r.confirmExisting(HASH,HASH,NOW.plusSeconds(2)));assertEquals(CredentialInstallationResult.INSTALLED,r.getResult());assertEquals(HASH,r.getPackageSha256());
    }
    @Test void invalidRevisionsHashesAndTimeAreRejected(){
        assertThrows(IllegalArgumentException.class,()->CredentialMetadata.revision(0));assertThrows(IllegalArgumentException.class,()->CredentialMetadata.hash(HASH.toUpperCase()));
        var r=receipt();assertThrows(IllegalArgumentException.class,()->r.confirmExisting(HASH,FINGERPRINT,NOW));
        assertThrows(IllegalStateException.class,()->new CredentialInstallationReceipt(UUID.randomUUID(),1,r.getAcademyInstance(),"K01",HASH,FINGERPRINT,NOW.plusSeconds(1),NOW));
    }
    @Test void persistenceRejectsIdentityTamperingAndNegativeVersion() throws Exception {
        var r=receipt();var validate=CredentialInstallationReceipt.class.getDeclaredMethod("validate");validate.setAccessible(true);validate.invoke(r);
        var identity=CredentialInstallationReceipt.class.getDeclaredField("academyInstanceId");identity.setAccessible(true);identity.set(r,UUID.randomUUID());assertThrows(java.lang.reflect.InvocationTargetException.class,()->validate.invoke(r));
        var second=receipt();var version=CredentialInstallationReceipt.class.getDeclaredField("version");version.setAccessible(true);version.setLong(second,-1);final var invalid=second;assertThrows(java.lang.reflect.InvocationTargetException.class,()->validate.invoke(invalid));
    }
}
