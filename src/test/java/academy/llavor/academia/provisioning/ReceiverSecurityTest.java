package academy.llavor.academia.provisioning;

import academy.llavor.academia.provisioning.config.*;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.security.*;
import academy.llavor.academia.provisioning.service.ProvisioningFailure;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.parameter.dto.*;
import academy.llavor.academia.parameter.entity.ParameterType;
import academy.llavor.academia.parameter.repository.AcademyParameterRepository;
import academy.llavor.academia.parameter.service.*;
import academy.llavor.academia.parameter.security.*;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;
import java.util.*;
import org.junit.jupiter.api.Test;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiverSecurityTest {
    private static final String PREFIX=AcademiaProvisioningConfiguration.PREFIX;
    private static void expect(ProvisioningFailure.Code code, Runnable action) {
        var error = assertThrows(ProvisioningFailure.class, action::run);
        assertEquals(code, error.getCode()); assertNull(error.getCause());
    }
    private static Config config(Map<String,String> values,String sourceName) {
        var config=mock(Config.class);var source=mock(ConfigSource.class);
        when(source.getName()).thenReturn(sourceName);
        when(source.getValue(anyString())).thenAnswer(c->values.get(c.getArgument(0)));
        when(config.getConfigSources()).thenReturn(List.of(source));return config;
    }
    private static Map<String,ParameterSnapshot> settings() {
        var values=new HashMap<String,ParameterSnapshot>();
        values.put(PREFIX+"enabled",new ParameterSnapshot(PREFIX+"enabled",ParameterType.BOOLEAN,true,false,"true"));
        values.put(PREFIX+"allow-loopback-http",new ParameterSnapshot(PREFIX+"allow-loopback-http",ParameterType.BOOLEAN,true,false,"false"));
        values.put(PREFIX+"accepted-key-ids",new ParameterSnapshot(PREFIX+"accepted-key-ids",ParameterType.TEXT,true,false,"K01"));
        return values;
    }
    private static AcademiaProvisioningConfiguration configuration(ReceiverFixture f,Map<String,ParameterSnapshot> values,Config external,boolean initialized) {
        var repo=mock(AcademyParameterRepository.class);
        when(repo.readBatch(any())).thenAnswer(c->Map.copyOf(values));
        var identity=mock(AcademyInstanceRepository.class);
        when(identity.find()).thenReturn(initialized?Optional.of(new AcademyInstance(f.academy,"ACADEMIA_001",f.portal)):Optional.empty());
        return new AcademiaProvisioningConfiguration(new ParameterConfigurationService(repo),identity,new ExpectedInstallationIdentity(external));
    }
    private static SensitiveParameterValue protectedValue(String value){try{
        var constructor=SensitiveParameterValue.class.getDeclaredConstructor(String.class);constructor.setAccessible(true);return constructor.newInstance(value);
    }catch(Exception ignored){throw new AssertionError("Synthetic verifier setup failed");}}
    private static ProvisioningAuthenticator authenticator(AcademyVerifierParameterReader reader){
        return new ProvisioningAuthenticator(new MysqlProvisioningCredentialVerifier(new LocalCredentialVerifier(reader)));
    }
    @Test void mysqlParametersMissingDisabledOrInvalidFailClosedWithoutInventingAnIdentity(){
        var f=new ReceiverFixture();var values=settings();var external=config(Map.of(ExpectedInstallationIdentity.PROPERTY,f.academy.toString()),"Application");
        expect(PROVISIONING_DISABLED,configuration(f,Map.of(),external,true)::loadEnabled);
        values.put(PREFIX+"enabled",new ParameterSnapshot(PREFIX+"enabled",ParameterType.BOOLEAN,false,false,"true"));
        expect(PROVISIONING_DISABLED,configuration(f,values,external,true)::loadEnabled);
        values.put(PREFIX+"enabled",new ParameterSnapshot(PREFIX+"enabled",ParameterType.TEXT,true,false,"true"));
        expect(INVALID_CONFIGURATION,configuration(f,values,external,true)::loadEnabled);
        expect(INVALID_CONFIGURATION,configuration(f,settings(),external,false)::loadEnabled);
    }
    @Test void identityComesFromLocalTableAndExternalPinMustBeIndependentAndCorrect(){
        var f=new ReceiverFixture();var values=settings();var pins=new HashMap<String,String>();pins.put(ExpectedInstallationIdentity.PROPERTY,f.academy.toString());
        var configuration=configuration(f,values,config(pins,"Application"),true);
        assertEquals(f.identity,configuration.loadEnabled());
        pins.put(ExpectedInstallationIdentity.PROPERTY,UUID.randomUUID().toString());expect(INVALID_CONFIGURATION,configuration::loadEnabled);
        pins.clear();expect(INVALID_CONFIGURATION,configuration::loadEnabled);
        expect(INVALID_CONFIGURATION,configuration(f,values,config(Map.of(ExpectedInstallationIdentity.PROPERTY,f.academy.toString()),"JDBC"),true)::loadEnabled);
        pins.put(ExpectedInstallationIdentity.PROPERTY,f.academy.toString());
        values.put(PREFIX+"accepted-key-ids",new ParameterSnapshot(PREFIX+"accepted-key-ids",ParameterType.TEXT,true,false,"K01,K01"));
        expect(INVALID_CONFIGURATION,configuration::loadEnabled);
    }
    @Test void credentialIsVerifiedLocallyAndRevocationTakesEffectOnTheNextRequest(){
        var f=new ReceiverFixture();var reader=mock(AcademyVerifierParameterReader.class);
        when(reader.read("K01")).thenReturn(protectedValue(f.verifier));var auth=authenticator(reader);
        var principal=auth.authenticate(f.identity,"Bearer "+f.token,"K01");
        assertEquals(f.portal,principal.portalId());assertEquals(f.academy,principal.academyId());assertEquals("K01",principal.getName());
        expect(UNAUTHORIZED,()->auth.authenticate(f.identity,null,"K01"));
        expect(UNAUTHORIZED,()->auth.authenticate(f.identity,"Bearer "+f.token,"UNKNOWN"));
        expect(UNAUTHORIZED,()->auth.authenticate(f.identity,"Basic "+f.token,"K01"));
        expect(UNAUTHORIZED,()->auth.authenticate(f.identity,"Bearer "+new ReceiverFixture().token,"K01"));
        doThrow(new ParameterAccessException(ParameterAccessException.Reason.NOT_FOUND)).when(reader).read("K01");
        expect(UNAUTHORIZED,()->auth.authenticate(f.identity,"Bearer "+f.token,"K01"));
        doThrow(new ParameterAccessException(ParameterAccessException.Reason.FORBIDDEN)).when(reader).read("K01");
        expect(UNAUTHORIZED,()->auth.authenticate(f.identity,"Bearer "+f.token,"K01"));
        doReturn(protectedValue("invalid")).when(reader).read("K01");
        expect(INVALID_CONFIGURATION,()->auth.authenticate(f.identity,"Bearer "+f.token,"K01"));
        doThrow(new ParameterAccessException(ParameterAccessException.Reason.STORAGE_FAILURE)).when(reader).read("K01");
        expect(TEMPORARILY_UNAVAILABLE,()->auth.authenticate(f.identity,"Bearer "+f.token,"K01"));
        verify(reader,times(6)).read("K01");
    }
    @Test void localConfigurationRevocationIsObservedWithoutRestartOrCache(){
        var f=new ReceiverFixture();var values=settings();var external=config(Map.of(ExpectedInstallationIdentity.PROPERTY,f.academy.toString()),"Application");
        var configuration=configuration(f,values,external,true);var reader=mock(AcademyVerifierParameterReader.class);
        when(reader.read("K01")).thenReturn(protectedValue(f.verifier));var auth=authenticator(reader);
        auth.authenticate(configuration.loadEnabled(),"Bearer "+f.token,"K01");
        values.put(PREFIX+"accepted-key-ids",new ParameterSnapshot(PREFIX+"accepted-key-ids",ParameterType.TEXT,true,false,"K02"));
        expect(UNAUTHORIZED,()->auth.authenticate(configuration.loadEnabled(),"Bearer "+f.token,"K01"));
        verify(reader,times(1)).read("K01");
        values.put(PREFIX+"enabled",new ParameterSnapshot(PREFIX+"enabled",ParameterType.BOOLEAN,true,false,"false"));
        expect(PROVISIONING_DISABLED,configuration::loadEnabled);
    }

    @Test void databaseUnavailableProducesOnlyRecoverableSafeDiagnostics(){
        var parameters=mock(ParameterConfigurationService.class);
        when(parameters.readBatch(any())).thenThrow(new ParameterAccessException(ParameterAccessException.Reason.STORAGE_FAILURE));
        var cfg=new AcademiaProvisioningConfiguration(parameters,mock(AcademyInstanceRepository.class),new ExpectedInstallationIdentity(config(Map.of(),"Application")));
        expect(TEMPORARILY_UNAVAILABLE,cfg::loadEnabled);
    }

    @Test void requestIdentityAndVersionMustMatchTheAuthenticatedConfiguredInstance() {
        var f = new ReceiverFixture();
        var request = f.request();
        expect(ACADEMY_IDENTITY_MISMATCH, () -> ProvisioningCommand.from(f.principal, UUID.randomUUID().toString(), f.classroom.toString(), f.operation.toString(), request));
        request.setPortalInstanceId(UUID.randomUUID().toString());
        expect(PORTAL_IDENTITY_MISMATCH, () -> ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), request));
        request.setPortalInstanceId(f.portal.toString()); request.setContractVersion(2);
        expect(UNSUPPORTED_CONTRACT_VERSION, () -> ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), request));
        request.setContractVersion(null);
        expect(INVALID_REQUEST, () -> ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), request));
        request.setContractVersion(1);
        expect(INVALID_REQUEST, () -> ProvisioningCommand.from(f.principal, "1-1-1-1-1", f.classroom.toString(), f.operation.toString(), request));
    }

    @Test void fingerprintIsDeterministicNormalizedAndSensitiveToEveryBusinessIdentityAndName() {
        var f = new ReceiverFixture(); var first = f.command();
        var request = f.request(); request.setClassroomName("  Aula inicial  ");
        var normalized = ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), request);
        assertEquals(first.fingerprint(), normalized.fingerprint()); assertEquals(64, first.fingerprint().length());
        request.setClassroomName("Cambio");
        assertNotEquals(first.fingerprint(), ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), request).fingerprint());
        assertNotEquals(first.fingerprint(), new ProvisioningCommand(first.portalId(), first.academyId(), first.classroomId(),
            UUID.randomUUID(), first.academyCode(), first.classroomCode(), first.classroomName()).fingerprint());
        for (String code : List.of("aula_001", "AULA-1", "A".repeat(81))) {
            request.setClassroomCode(code);
            expect(INVALID_REQUEST, () -> ProvisioningCommand.from(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), request));
        }
    }

    @Test void identityProbeRechecksPersistentIdentityPinAndAcceptedKeyWithoutAnyProvisioningWrite() {
        var f = new ReceiverFixture();
        var values = settings();
        var pins = new HashMap<String, String>();
        pins.put(ExpectedInstallationIdentity.PROPERTY, f.academy.toString());
        var cfg = configuration(f, values, config(pins, "Application"), true);
        var transactions = mock(academy.llavor.academia.provisioning.service.ProvisioningTransactions.class);
        var service = new academy.llavor.academia.provisioning.service.ClassroomProvisioningService(transactions, cfg);
        assertEquals(f.portal.toString(), service.getIdentity(f.principal, f.academy.toString()).getPortalInstanceId());
        pins.put(ExpectedInstallationIdentity.PROPERTY, UUID.randomUUID().toString());
        expect(INVALID_CONFIGURATION, () -> service.getIdentity(f.principal, f.academy.toString()));
        pins.put(ExpectedInstallationIdentity.PROPERTY, f.academy.toString());
        var contradictory = new AcademiaIdentity(f.academy, "ACADEMIA_001", UUID.randomUUID(), f.identity.verifierParameters(), false);
        var wrongPortal = new ProvisioningAuthenticator((accepted, key, token) -> true)
            .authenticate(contradictory, "Bearer " + f.token, "K01");
        expect(PORTAL_IDENTITY_MISMATCH, () -> service.getIdentity(wrongPortal, f.academy.toString()));
        values.put(PREFIX + "accepted-key-ids", new ParameterSnapshot(PREFIX + "accepted-key-ids", ParameterType.TEXT, true, false, "K02"));
        expect(UNAUTHORIZED, () -> service.getIdentity(f.principal, f.academy.toString()));
        values.put(PREFIX + "enabled", new ParameterSnapshot(PREFIX + "enabled", ParameterType.BOOLEAN, true, false, "false"));
        expect(PROVISIONING_DISABLED, () -> service.getIdentity(f.principal, f.academy.toString()));
        expect(UNAUTHORIZED, () -> service.getIdentity(null, f.academy.toString()));
        verifyNoInteractions(transactions);
    }
}
