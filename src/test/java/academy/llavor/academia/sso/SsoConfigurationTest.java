package academy.llavor.academia.sso;

import academy.llavor.academia.provisioning.config.ExpectedInstallationIdentity;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.parameter.dto.ParameterReadResult;
import academy.llavor.academia.parameter.service.ParameterConfigurationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static academy.llavor.academia.sso.SsoClientFailure.Code.*;

class SsoConfigurationTest {
    static final UUID A=UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID P=UUID.fromString("22222222-2222-4222-8222-222222222222");
    static final UUID C=UUID.fromString("33333333-3333-4333-8333-333333333333");
    static final String SECRET="A".repeat(43);
    ParameterConfigurationService parameters; AcademyInstanceRepository identities;
    LocalClassroomRepository classrooms; ExpectedInstallationIdentity pin;
    Map<String,ParameterReadResult<Object>> values; SsoConfiguration configuration;
    @BeforeEach void setup(){
        parameters=mock(ParameterConfigurationService.class);identities=mock(AcademyInstanceRepository.class);
        classrooms=mock(LocalClassroomRepository.class);pin=mock(ExpectedInstallationIdentity.class);
        when(pin.require(A)).thenReturn(A);
        when(identities.find()).thenReturn(Optional.of(new AcademyInstance(A,"ACADEMY_TEST",P)));
        when(classrooms.findByIdentity(P,C)).thenReturn(Optional.of(new LocalClassroom(P,C,A,"ROOM_TEST","Synthetic")));
        values=new HashMap<>();put("enabled",true);put("portal-base-url",URI.create("https://portal.example.test/llavor-portal"));
        put("active-key-id","S01");when(parameters.readBatch(SsoConfiguration.KEYS)).thenAnswer(i->Map.copyOf(values));
        configuration=new SsoConfiguration(parameters,identities,classrooms,pin);
    }
    void put(String suffix,Object value){values.put(SsoConfiguration.PREFIX+suffix,new ParameterReadResult<>(ParameterReadResult.Status.VALID,value));}
    void failure(SsoClientFailure.Code code){assertEquals(code,assertThrows(SsoClientFailure.class,()->configuration.loadEnabled(C)).getCode());}
    @Test void httpsAndIndependentDefaults(){
        var settings=configuration.loadEnabled(C);assertEquals(A,settings.academyId());assertEquals(P,settings.portalId());
        assertEquals(3,settings.connectTimeout().toSeconds());assertEquals(15,settings.requestTimeout().toSeconds());
        assertTrue(SsoConfiguration.KEYS.stream().allMatch(k->k.startsWith("llavor.academia.sso.")));
    }
    @Test void disabledByDefaultBeforeIdentityReads(){values.clear();failure(DISABLED);verifyNoInteractions(identities,classrooms,pin);}
    @ParameterizedTest @ValueSource(strings={"portal-base-url","active-key-id"})
    void noImplicitRequiredValues(String key){values.remove(SsoConfiguration.PREFIX+key);failure(INVALID_CONFIGURATION);}
    @Test void localHttpNeedsBothDevelopmentAndExplicitFlag(){
        put("portal-base-url",URI.create("http://localhost:8080/llavor-portal"));failure(INVALID_CONFIGURATION);
        put("allow-loopback-http",true);failure(INVALID_CONFIGURATION);
        put("environment","DEVELOPMENT");assertEquals("http",configuration.loadEnabled(C).portalBaseUrl().getScheme());
        put("allow-loopback-http",false);failure(INVALID_CONFIGURATION);
    }
    @ParameterizedTest @ValueSource(strings={"http://elsewhere.test/portal","http://localhost.evil.test/portal",
            "https://u:p@portal.test/portal","https://portal.test/portal?redirect=x",
            "https://portal.test/portal#fragment","https://portal.test/a/../b","https://portal.test/a%2fb",
            "https://portal.test/portal/","https://portal.test/a//b"})
    void rejectsAmbiguousOrNonlocalTargets(String url){
        put("environment","DEVELOPMENT");put("allow-loopback-http",true);put("portal-base-url",URI.create(url));failure(INVALID_CONFIGURATION);
    }
    @Test void timeoutAndTypeValidation(){put("connect-timeout-seconds",31L);failure(INVALID_CONFIGURATION);
        put("connect-timeout-seconds",3L);put("enabled","true");failure(INVALID_CONFIGURATION);}
    @Test void invalidDisabledConfigurationDoesNotFallback(){values.put(SsoConfiguration.PREFIX+"allow-loopback-http",ParameterReadResult.state(ParameterReadResult.Status.INVALID));failure(INVALID_CONFIGURATION);}
    @Test void identityAndLocalRoomRequired(){
        when(pin.require(A)).thenThrow(new IllegalStateException("synthetic secret"));failure(IDENTITY_MISMATCH);
        doReturn(A).when(pin).require(A);when(classrooms.findByIdentity(P,C)).thenReturn(Optional.empty());failure(IDENTITY_MISMATCH);
    }
    @Test void wrongLocalAcademyDenied(){
        when(classrooms.findByIdentity(P,C)).thenReturn(Optional.of(new LocalClassroom(P,C,P,"ROOM_TEST","Synthetic")));failure(IDENTITY_MISMATCH);
    }
    @Test void browserRequiresExplicitOwnUrlAndUsesIndependentConfiguration(){
        assertEquals(INVALID_CONFIGURATION,assertThrows(SsoClientFailure.class,()->configuration.loadBrowserEnabled(C)).getCode());
        var own=Map.of(SsoConfiguration.PREFIX+"academy-base-url",new ParameterReadResult<Object>(ParameterReadResult.Status.VALID,URI.create("https://academy.example.test/llavor-academia")));
        when(parameters.readBatch(List.of(SsoConfiguration.PREFIX+"academy-base-url"))).thenReturn(own);
        assertEquals("https://academy.example.test",configuration.loadBrowserEnabled(C).academyOrigin());
    }
    @Test void credentialClosedCannotBeReused(){
        var credential=new SsoCredential(SECRET);credential.close();
        assertThrows(SsoClientFailure.class,()->credential.use(v->v));
    }
}
