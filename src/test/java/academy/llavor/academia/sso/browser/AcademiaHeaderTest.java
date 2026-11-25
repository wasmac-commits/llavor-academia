package academy.llavor.academia.sso.browser;
import java.nio.file.*;
import academy.llavor.academia.testing.FaceletView;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AcademiaHeaderTest {
    static final Path WEB=Path.of("src/main/webapp"),PORTAL=Path.of("../llavor-portal/src/main/webapp");
    @Test void logoAndThemeAreExactPortalResourcesAndLocalToAcademia()throws Exception{
        for(String file:new String[]{"resources/llavor/css/llavor-theme.css","resources/llavor/css/language-selector.css","resources/llavor/images/llavor-academy-horizontal.png","resources/llavor/images/llavor-header-white.jpg","resources/llavor/css/platform-header.css"})assertArrayEquals(Files.readAllBytes(PORTAL.resolve(file)),Files.readAllBytes(WEB.resolve(file)),file);
        String theme=Files.readString(WEB.resolve("resources/llavor/css/llavor-theme.css"));assertTrue(theme.contains("--llavor-header-height: 64px"));assertTrue(theme.contains("max-width: 760px"));assertTrue(theme.contains("width: 150px; height: 50px"));
        String content=Files.readString(WEB.resolve("resources/llavor/css/academia.css"));for(String forbidden:new String[]{".llavor-topbar",".llavor-header-logo","body{","body {",".academy-header",".brand{"})assertFalse(content.contains(forbidden));
    }
    @Test void headerKeepsLocalLogoutAndHumanScopeWithoutPortalActions()throws Exception{
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        var doc=FaceletView.composed("index.xhtml");assertEquals(1,doc.getElementsByTagName("header").getLength());assertEquals(1,doc.getElementsByTagNameNS("jakarta.faces.html","graphicImage").getLength());
        var image=(org.w3c.dom.Element)doc.getElementsByTagNameNS("jakarta.faces.html","graphicImage").item(0);assertEquals("llavor",image.getAttribute("library"));assertEquals("images/llavor-header-white.jpg",image.getAttribute("name"));assertEquals("204",image.getAttribute("width"));assertEquals("68",image.getAttribute("height"));
        String view=FaceletView.text("index.xhtml");for(String expected:new String[]{"llavor-topbar","llavor-topbar-right","llavor-brand-link","llavor-user-identity","llavor-logout-button","#{academyHome.displayName}","#{academyHome.classroomCode}","#{academyHome.classroomName}","#{academyHome.csrf}","#{request.contextPath}/sso/logout","method=\"post\"","outcome=\"/index.xhtml\""})assertTrue(view.contains(expected),expected);
        for(String forbidden:new String[]{"portalSession","/llavor-portal","/admin/","classroom-selector.xhtml","currentUserEmail"})assertFalse(view.contains(forbidden),forbidden);
        assertTrue(view.indexOf("css/llavor-theme.css")<view.indexOf("css/academy-shell.css"));
    }
    @Test void composedHeaderKeepsPostLogoutCsrfAndSessionIdentityInTheirActualControls()throws Exception{
        var doc=FaceletView.composed("index.xhtml");
        var forms=doc.getElementsByTagNameNS("http://www.w3.org/1999/xhtml","form");
        assertEquals(1,forms.getLength());var logout=(org.w3c.dom.Element)forms.item(0);
        assertEquals("post",logout.getAttribute("method"));assertEquals("#{request.contextPath}/sso/logout",logout.getAttribute("action"));
        var token=(org.w3c.dom.Element)logout.getElementsByTagNameNS("http://www.w3.org/1999/xhtml","input").item(0);
        assertEquals("hidden",token.getAttribute("type"));assertEquals("csrf",token.getAttribute("name"));assertEquals("#{academyHome.csrf}",token.getAttribute("value"));
        var submit=(org.w3c.dom.Element)logout.getElementsByTagNameNS("http://www.w3.org/1999/xhtml","button").item(0);assertEquals("submit",submit.getAttribute("type"));
        assertEquals(1,doc.getElementsByTagNameNS("jakarta.faces.html","body").getLength());
        assertEquals(1,doc.getElementsByTagNameNS("http://www.w3.org/1999/xhtml","main").getLength());
        assertEquals(1,doc.getElementsByTagNameNS("http://www.w3.org/1999/xhtml","aside").getLength());
    }

    @Test void responsiveControlsAreSingleInstancesAndSidebarUsesRealSessionData()throws Exception{
        var doc=FaceletView.composed("index.xhtml");
        var menus=doc.getElementsByTagNameNS("http://primefaces.org/ui","selectOneMenu");assertEquals(1,menus.getLength());
        var menu=(org.w3c.dom.Element)menus.item(0);assertEquals("#{academyLanguageBean.selectedLanguage}",menu.getAttribute("value"));assertEquals("academy-language-panel",menu.getAttribute("panelStyleClass"));
        var ajax=(org.w3c.dom.Element)menu.getElementsByTagNameNS("http://primefaces.org/ui","ajax").item(0);assertEquals("#{academyLanguageBean.change}",ajax.getAttribute("listener"));assertEquals("@form",ajax.getAttribute("process"));
        var forms=doc.getElementsByTagNameNS("jakarta.faces.html","form");assertEquals(1,forms.getLength());assertEquals("academyLocaleForm",((org.w3c.dom.Element)forms.item(0)).getAttribute("id"));
        var sidebar=Files.readString(WEB.resolve("WEB-INF/fragments/academy-sidebar.xhtml"));assertTrue(sidebar.contains("#{academyHome.displayName}"));assertTrue(sidebar.contains("#{academyHome.classroomCode}"));assertTrue(sidebar.contains("academy-session-controls.xhtml"));
        var header=Files.readString(WEB.resolve("WEB-INF/fragments/academy-header.xhtml"));assertFalse(header.contains("<h:form"));assertTrue(header.contains("academy-header-session"));
        var css=Files.readString(WEB.resolve("resources/llavor/css/academy-navigation.css"));assertTrue(css.contains("@media(max-width:1023px)"));assertTrue(css.contains("margin-left:auto"));assertTrue(css.contains("overflow-y:auto"));
    }

}
