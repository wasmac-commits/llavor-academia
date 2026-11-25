package academy.llavor.academia.sso.browser;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;
class AcademyPublicUrlsTest {
 @Test void publicBrowserUrlsNeverReplaceTheInternalClientUrl(){
  var technical=BrowserFixture.settings(false).technical();
  var browser=new BrowserSettings(technical,URI.create("https://public.example.test/llavor-academia"),URI.create("https://public.example.test/llavor-portal"));
  assertEquals("https://public.example.test",browser.portalOrigin());assertEquals("localhost",browser.technical().portalBaseUrl().getHost());
  assertThrows(RuntimeException.class,()->new BrowserSettings(technical,URI.create("https://public.example.test/llavor-academia"),URI.create("http://other.example.test/llavor-portal")));
 }
}
