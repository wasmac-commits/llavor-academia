package academy.llavor.academia.i18n;
import academy.llavor.academia.i18n.core.*;
import academy.llavor.academia.i18n.entity.LocalCatalog;
import academy.llavor.academia.i18n.repository.LocalCatalogRepository;
import academy.llavor.academia.i18n.service.LocalTranslationService;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.provisioning.config.ExpectedInstallationIdentity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LocalCatalogTest {
    static CatalogSnapshot version(long version,String greeting){var old=CatalogContractTest.snapshot();
        return new CatalogSnapshot(1,old.portalInstanceId(),"ui",version,old.publishedAt().plusSeconds(version),old.languages(),Map.of("es",Map.of("common.greeting",greeting),"ca-ES-valencia",Map.of()));}
    static boolean install(LocalCatalog local,CatalogSnapshot snapshot){String json=CatalogCodec.encode(snapshot);return local.install(snapshot,json,CatalogCodec.digest(json),CatalogContractTest.PORTAL);}
    @Test void validReplacementIsAtomicAndOldOrConflictingVersionsDoNotEraseLastValid(){
        var local=new LocalCatalog();assertEquals(0,local.snapshot(CatalogContractTest.PORTAL).version());
        assertTrue(install(local,version(1,"Hola, {0}")));assertFalse(install(local,version(1,"Hola, {0}")));
        assertTrue(install(local,version(2,"Bienvenido, {0}")));
        assertThrows(IllegalArgumentException.class,()->install(local,version(1,"Hola, {0}")));
        assertThrows(IllegalArgumentException.class,()->install(local,version(2,"Other {0}")));
        assertEquals("Bienvenido, Ana",local.snapshot(CatalogContractTest.PORTAL).resolve("ca-ES-valencia","common.greeting","Ana").text());
    }
    @Test void foreignPortalOrCatalogAndFuturePublicationsAreRejected(){
        var local=new LocalCatalog();var base=version(1,"Hola, {0}");install(local,base);
        for(var foreign:List.of(new CatalogSnapshot(1,UUID.randomUUID(),"ui",2,base.publishedAt(),base.languages(),base.messages()),
            new CatalogSnapshot(1,base.portalInstanceId(),"other",2,base.publishedAt(),base.languages(),base.messages()),
            new CatalogSnapshot(1,base.portalInstanceId(),"ui",2,Instant.now().plusSeconds(3600),base.languages(),base.messages()))){
            assertThrows(IllegalArgumentException.class,()->install(local,foreign));
        }
        assertEquals(base,local.snapshot(CatalogContractTest.PORTAL));
        assertThrows(IllegalStateException.class,()->local.snapshot(UUID.randomUUID()));
    }
    @Test void corruptPayloadNeverChangesTheAcceptedVersion(){
        var local=new LocalCatalog();var base=version(1,"Hola, {0}");install(local,base);
        String next=CatalogCodec.encode(version(2,"Other {0}"));
        assertThrows(IllegalArgumentException.class,()->local.install(version(2,"Other {0}"),next,"0".repeat(64),CatalogContractTest.PORTAL));
        assertEquals(base,local.snapshot(CatalogContractTest.PORTAL));
    }
    @Test void schemaNotYetInstalledUsesSpanishWithoutReadingAnAbsentTable(){
        var repository=mock(LocalCatalogRepository.class);var identities=mock(AcademyInstanceRepository.class);var pin=mock(ExpectedInstallationIdentity.class);
        var academy=new AcademyInstance(UUID.randomUUID(),"LOCAL_TEST",CatalogContractTest.PORTAL);when(identities.find()).thenReturn(Optional.of(academy));when(pin.require(academy.getInstanceId())).thenReturn(academy.getInstanceId());
        assertEquals(0,new LocalTranslationService(repository,identities,pin).snapshot().version());verify(repository,never()).require(anyBoolean());
    }
    @Test void serviceRestartsReadThePersistedPublicationWithoutAnyPortalClient(){
        var repository=mock(LocalCatalogRepository.class);when(repository.installed()).thenReturn(true);var identities=mock(AcademyInstanceRepository.class);var pin=mock(ExpectedInstallationIdentity.class);
        var academy=new AcademyInstance(UUID.randomUUID(),"LOCAL_TEST",CatalogContractTest.PORTAL);
        when(identities.find()).thenReturn(Optional.of(academy));when(pin.require(academy.getInstanceId())).thenReturn(academy.getInstanceId());
        var persisted=new LocalCatalog();when(repository.require(anyBoolean())).thenReturn(persisted);
        var importer=new LocalTranslationService(repository,identities,pin);var snapshot=version(1,"Hola, {0}");String json=CatalogCodec.encode(snapshot);
        assertTrue(importer.install(json,CatalogCodec.digest(json)));verify(repository).require(true);verify(repository).flush();
        var reader=new LocalTranslationService(repository,identities,pin);assertEquals(snapshot,reader.snapshot());
        assertEquals("Hola, Ana",reader.resolve("ca-ES-valencia","common.greeting","Ana").text());
        clearInvocations(repository,identities,pin);var detached=reader.snapshot();clearInvocations(repository,identities,pin);
        detached.resolve("es","common.greeting","Ana");verifyNoInteractions(repository,identities,pin);
        assertEquals(Transactional.TxType.REQUIRES_NEW,LocalTranslationService.class.getAnnotation(Transactional.class).value());
    }
}
