package academy.llavor.academia.i18n;
import academy.llavor.academia.i18n.seed.AcademyInitialTexts;
import academy.llavor.academia.testing.FaceletView;
import jakarta.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AcademyLanguageViewTest {
    @Test void functionalViewsUsePreparedKeysAndLocalLocaleWithoutTranslatingUserData()throws Exception{
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        for(String file:List.of("index.xhtml","access-required.xhtml")){
            var path=Path.of("src/main/webapp",file);var doc=FaceletView.composed(file);assertEquals("#{academyLanguageBean.languageTag}",doc.getDocumentElement().getAttribute("lang"));assertEquals(1,doc.getElementsByTagNameNS("jakarta.faces.core","view").getLength());
            String text=FaceletView.text(file);var keys=Pattern.compile("academyLanguageBean[.]text\\['([^']+)'\\]").matcher(text);while(keys.find())assertTrue(AcademyInitialTexts.spanish().containsKey(keys.group(1)),keys.group(1));assertFalse(text.contains("escape=\"false\""));
        }
        var home=FaceletView.text("index.xhtml");for(String binding:List.of("#{academyHome.classroomName}","#{academyHome.classroomCode}","#{academyHome.academyCode}","#{academyHome.csrf}"))assertTrue(home.contains(binding));
        assertTrue(home.contains("#{academyLanguageBean.format('common.greeting', academyHome.displayName)}"));
    }
    @Test void selectorMatchesPortalVisualContractAndSeedsBelongToCentralCatalog()throws Exception{
        String home=FaceletView.text("index.xhtml"),portal=Files.readString(Path.of("../llavor-portal/src/main/webapp/WEB-INF/templates/portal-template.xhtml"));
        for(String token:List.of("llavor-locale-form","llavor-language-icon","llavor-locale-select","itemLabel=\"#{language.nativeName}\"","name=\"languageCsrf\"","partialSubmit=\"false\"")){assertTrue(home.contains(token));assertTrue(portal.contains(token));}
        String css=Files.readString(Path.of("src/main/webapp/resources/llavor/css/language-selector.css"));assertTrue(css.contains("background:transparent!important"));assertTrue(css.contains("ui-state-focus"));assertTrue(css.contains("@media(max-width:760px)"));
        try(var reader=Json.createReader(Files.newBufferedReader(Path.of("../llavor-portal/src/main/resources/i18n/portal-initial-texts.json")))){
            var central=reader.readObject().getJsonArray("texts");for(var entry:AcademyInitialTexts.entries()){
                // Stage 21 drafts must not masquerade as published central translations.
                if(entry.key().startsWith("academy.shell.")){
                    assertFalse(entry.spanish().isBlank());assertFalse(entry.valencian().isBlank());
                    assertEquals(academy.llavor.academia.i18n.core.TextTemplate.arguments(entry.spanish()),academy.llavor.academia.i18n.core.TextTemplate.arguments(entry.valencian()));
                    assertTrue(entry.description().contains("no publicada automáticamente"));continue;
                }
                var row=central.stream().map(v->v.asJsonObject()).filter(v->v.getString("key").equals(entry.key())).findFirst().orElseThrow();assertEquals(row.getString("es"),entry.spanish());assertEquals(row.getString("ca-ES-valencia"),entry.valencian());
            }
        }
    }
}
