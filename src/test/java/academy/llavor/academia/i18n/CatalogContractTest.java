package academy.llavor.academia.i18n;
import academy.llavor.academia.i18n.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CatalogContractTest {
    static final UUID PORTAL=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    static CatalogSnapshot snapshot(){return new CatalogSnapshot(1,PORTAL,"ui",1,Instant.parse("2026-01-01T00:00:00Z"),
        Map.of("es","Espa\u00f1ol","ca-ES-valencia","Valenci\u00e0"),Map.of(
            "es",Map.of("common.greeting","Hola, {0}","common.action.continue","Continuar","portal.title","Portal","academy.title","Academia"),
            "ca-ES-valencia",Map.of("common.greeting","Benvingut a l'aula, {0}","academy.title","Acad\u00e8mia")));}
    @ParameterizedTest @CsvSource({"ES,es","CA-es-VALENCIA,ca-ES-valencia","en-us,en-US","fr,fr","zh-hant-tw,zh-Hant-TW"})
    void canonicalJavaLocaleTags(String input,String expected){assertEquals(expected,LanguageTags.normalize(input));assertEquals(expected,LanguageTags.normalize(expected));}
    @ParameterizedTest @ValueSource(strings={"","ca_ES_valencia"," es","es ","und","x-private","en-u-ca-gregory","es--ES","en-US-US"})
    void invalidOrExtendedTagsAreRejected(String tag){assertThrows(IllegalArgumentException.class,()->LanguageTags.normalize(tag));}
    @Test void valencianVariantPreservesRegionalIdentityOnJdk21(){
        var locale=java.util.Locale.forLanguageTag(LanguageTags.normalize("CA-es-VALENCIA"));
        assertEquals("ca",locale.getLanguage());assertEquals("ES",locale.getCountry());assertEquals("valencia",locale.getVariant());
    }
    @Test void resolutionHandlesSparseDisabledAndMissingMessages(){
        var snapshot=snapshot();
        assertEquals("Benvingut a l'aula, Joan",snapshot.resolve("ca-ES-valencia","common.greeting","Joan").text());
        assertFalse(snapshot.resolve("CA-es-VALENCIA","common.greeting","Joan").fallback());
        assertEquals("Continuar",snapshot.resolve("ca-ES-valencia","common.action.continue").text());
        assertTrue(snapshot.resolve("ca-ES-valencia","common.action.continue").fallback());
        assertEquals("Hola, Ana",snapshot.resolve("en","common.greeting","Ana").text());
        assertEquals("es",snapshot.resolve("invalid_tag","common.action.continue").language());
        assertTrue(snapshot.resolve("es","common.not_defined").missing());
        assertEquals("[common.not_defined]",snapshot.resolve("es","common.not_defined").text());
        assertEquals("Portal",snapshot.resolve("es","portal.title").text());assertEquals("Academia",snapshot.resolve("es","academy.title").text());
    }
    @Test void placeholdersArePlainTextAndApostrophesStayLiteral(){
        assertEquals("L'aula de <b>Joan</b> {ok}",TextTemplate.format("L'aula de {0} {{ok}}","<b>Joan</b>"));
        assertEquals("#{bean.admin}",TextTemplate.format("{0}","#{bean.admin}"));
        assertThrows(IllegalArgumentException.class,()->TextTemplate.format("{0}"));
        assertThrows(IllegalArgumentException.class,()->TextTemplate.format("{0}",new Object()));
        for(String text:List.of("{0,number}","{20}","{00}","{","}","\u0000"))assertThrows(IllegalArgumentException.class,()->TextTemplate.arguments(text));
    }
    @Test void snapshotsAreDeeplyImmutableAndRequireCompleteSpanishBase(){
        var snapshot=snapshot();assertThrows(UnsupportedOperationException.class,()->snapshot.messages().get("es").put("common.x","x"));
        assertThrows(UnsupportedOperationException.class,()->snapshot.languages().clear());
        assertThrows(IllegalArgumentException.class,()->new CatalogSnapshot(1,PORTAL,"ui",2,Instant.now(),Map.of("ca","Catal\u00e0"),Map.of("ca",Map.of("common.x","x"))));
        assertThrows(IllegalArgumentException.class,()->new CatalogSnapshot(1,PORTAL,"ui",2,Instant.now(),Map.of("es","Spanish","ca","Catalan"),Map.of("es",Map.of("common.x","{0}"),"ca",Map.of("common.x","{1}"))));
    }
    @Test void contractRoundTripsWithStableBytesAndRejectsTampering(){
        String json=CatalogCodec.encode(snapshot());String digest=CatalogCodec.digest(json);
        assertEquals(snapshot(),CatalogCodec.decode(json,digest));assertEquals(json,CatalogCodec.encode(CatalogCodec.decode(json,digest)));
        assertEquals(snapshot(),CatalogCodec.decode(json.getBytes(StandardCharsets.UTF_8),digest));
        assertThrows(IllegalArgumentException.class,()->CatalogCodec.decode(json+" ",digest));
        for(String bad:List.of(json.replace("\"formatVersion\":1","\"formatVersion\":2"),json.replaceFirst("\\{","{\"version\":1,"),json+" {}",json.replace("\"es\":\"Espa\u00f1ol\"","\"es\":\"Spanish\",\"es\":\"Other\""))){
            assertThrows(IllegalArgumentException.class,()->CatalogCodec.decode(bad,CatalogCodec.digest(bad)));
        }
        assertThrows(IllegalArgumentException.class,()->CatalogCodec.decode(new byte[]{(byte)0xc3,0x28},digest));
        String huge="x".repeat(CatalogCodec.MAX_BYTES+1);assertThrows(IllegalArgumentException.class,()->CatalogCodec.decode(huge,CatalogCodec.digest(huge)));
    }
    @Test void sharedGoldenPublicationRemainsCompatible()throws Exception{
        String json=new String(getClass().getResourceAsStream("/i18n/published-v1.json").readAllBytes(),StandardCharsets.UTF_8);
        assertEquals("4dd0f9dff5bfdb7ad0ec26ab4457b54a599162e97199127c37aad5becae220c2",CatalogCodec.digest(json));
        var snapshot=CatalogCodec.decode(json,CatalogCodec.digest(json));
        assertEquals("ca-ES-valencia",snapshot.effectiveLanguage("CA-es-VALENCIA"));assertEquals(1,snapshot.version());
        assertEquals("Continuar",snapshot.resolve("ca-ES-valencia","common.action.continue").text());
    }
    @Test void bootstrapContainsSpanishWithoutAnyPublishedCatalog(){
        var snapshot=BootstrapCatalog.spanish(PORTAL);assertEquals(0,snapshot.version());assertEquals(Set.of("es"),snapshot.languages().keySet());
        assertEquals("Continuar",snapshot.resolve("ca-ES-valencia","common.action.continue").text());
    }
}
