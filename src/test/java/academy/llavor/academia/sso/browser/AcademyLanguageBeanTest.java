package academy.llavor.academia.sso.browser;
import academy.llavor.academia.i18n.web.AcademyLanguageBean;
import academy.llavor.academia.i18n.core.*;
import academy.llavor.academia.i18n.seed.AcademyInitialTexts;
import academy.llavor.academia.i18n.service.LocalTranslationService;
import academy.llavor.academia.sso.browser.HumanSession;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AcademyLanguageBeanTest {
    LocalTranslationService catalogs;FacesContext faces;ExternalContext external;HttpServletRequest request;HttpSession session;UIViewRoot view;AcademyLanguageBean bean;
    Map<String,Object> attributes;
    static CatalogSnapshot published(boolean valencian){
        var names=valencian?Map.of("es","Español","ca-ES-valencia","Valencià"):Map.of("es","Español");
        var messages=new HashMap<String,Map<String,String>>();messages.put("es",AcademyInitialTexts.spanish());
        if(valencian){var texts=new HashMap<String,String>();AcademyInitialTexts.entries().forEach(e->texts.put(e.key(),e.valencian()));messages.put("ca-ES-valencia",texts);}
        return new CatalogSnapshot(1,UUID.fromString("12345678-1234-4234-8234-123456789abc"),"ui",1,Instant.parse("2026-11-23T08:00:00Z"),names,messages);
    }
    @BeforeEach void setup(){
        catalogs=mock(LocalTranslationService.class);faces=mock(FacesContext.class);external=mock(ExternalContext.class);request=mock(HttpServletRequest.class);session=mock(HttpSession.class);view=mock(UIViewRoot.class);
        when(catalogs.snapshot()).thenReturn(published(true));when(faces.getExternalContext()).thenReturn(external);when(faces.getViewRoot()).thenReturn(view);when(external.getRequest()).thenReturn(request);when(external.getRequestContextPath()).thenReturn("/llavor-academia");
        when(request.getSession(false)).thenReturn(session);when(request.getMethod()).thenReturn("POST");when(request.getParameter("languageCsrf")).thenReturn("CSRF");attributes=new HashMap<>();
        when(session.getAttribute(anyString())).thenAnswer(i->attributes.get(i.getArgument(0)));doAnswer(i->{attributes.put(i.getArgument(0),i.getArgument(1));return null;}).when(session).setAttribute(anyString(),any());
        var human=new HumanSession(BrowserFixture.grant(),BrowserFixture.NOW,"CSRF",BrowserFixture.settings(false).academyBaseUrl());attributes.put(HumanSession.ATTRIBUTE,human);
        bean=new AcademyLanguageBean(catalogs,faces);
    }
    @Test void changeUsesLocalPublishedCatalogAndRetainsChoiceBetweenRequests()throws Exception{
        bean.setSelectedLanguage("CA-es-VALENCIA");bean.change();assertEquals("ca-ES-valencia",bean.getLanguageTag());verify(view).setLocale(Locale.forLanguageTag("ca-ES-valencia"));verify(external).redirect("/llavor-academia/index.xhtml");
        var next=new AcademyLanguageBean(catalogs,faces);assertEquals("ca-ES-valencia",next.getLanguageTag());assertEquals("Acadèmia",next.getText().get("academy.header.context"));
    }
    @Test void repeatedElExpressionsReadTheLocalServiceOnlyOnce(){for(int i=0;i<100;i++){bean.getText();bean.getLocale();bean.getLanguages();}verify(catalogs,times(1)).snapshot();}
    @Test void noLocalTableOrNoPublicationUsesOnlySpanish(){
        when(catalogs.snapshot()).thenThrow(new IllegalStateException("NOT_INSTALLED"));assertEquals("Mi aula · Llavor Academia",bean.getText().get("academy.home.page_title"));assertEquals("es",bean.getLanguageTag());assertFalse(bean.isCanChange());
        doReturn(BootstrapCatalog.spanish(published(true).portalInstanceId())).when(catalogs).snapshot();assertEquals(1,new AcademyLanguageBean(catalogs,faces).getLanguages().size());
    }
    @Test void disabledLanguagesAndSparseTranslationsFallbackToSpanish()throws Exception{
        when(catalogs.snapshot()).thenReturn(published(false));bean.setSelectedLanguage("ca-ES-valencia");bean.change();verify(external,never()).redirect(anyString());assertEquals("es",bean.getLanguageTag());
        var full=published(true);when(catalogs.snapshot()).thenReturn(new CatalogSnapshot(1,full.portalInstanceId(),"ui",1,full.publishedAt(),full.languages(),Map.of("es",full.messages().get("es"),"ca-ES-valencia",Map.of())));
        attributes.put(AcademyLanguageBean.class.getName()+".choice","ca-ES-valencia");assertEquals("Academia",new AcademyLanguageBean(catalogs,faces).getText().get("academy.header.context"));
    }
    @Test void getForgedCsrfAndMissingSessionCannotChangePreference()throws Exception{
        bean.setSelectedLanguage("ca-ES-valencia");when(request.getMethod()).thenReturn("GET");bean.change();when(request.getMethod()).thenReturn("POST");when(request.getParameter("languageCsrf")).thenReturn("FORGED");bean.change();when(request.getSession(false)).thenReturn(null);bean.change();
        verify(external,never()).redirect(anyString());assertFalse(attributes.containsKey(AcademyLanguageBean.class.getName()+".choice"));
    }
    @Test void losingSessionDoesNotInventSharedSsoPreference(){((HumanSession)attributes.get(HumanSession.ATTRIBUTE)).chooseLanguage("ca-ES-valencia");assertEquals("ca-ES-valencia",bean.getLanguageTag());when(request.getSession(false)).thenReturn(null);assertEquals("es",new AcademyLanguageBean(catalogs,faces).getLanguageTag());}
    void exchange(academy.llavor.academia.sso.I18nHttpClient exchange)throws Exception{var field=AcademyLanguageBean.class.getDeclaredField("exchange");field.setAccessible(true);field.set(bean,exchange);}
    @Test void ssoLanguageInitializesLocalLocaleOnlyWhenPublishedLocally(){
        var old=BrowserFixture.grant();var extended=new academy.llavor.academia.sso.SsoAuthorization(old.accessId(),old.portalId(),old.userId(),old.academyId(),old.classroomId(),old.authorizedAt(),old.accessExpiresAt(),old.displayName(),"ca-ES-valencia",4);
        attributes.put(HumanSession.ATTRIBUTE,new HumanSession(extended,BrowserFixture.NOW,"CSRF",BrowserFixture.settings(false).academyBaseUrl()));assertEquals("ca-ES-valencia",bean.getLanguageTag());
        when(catalogs.snapshot()).thenReturn(published(false));assertEquals("es",new AcademyLanguageBean(catalogs,faces).getLanguageTag());
    }
    @Test void changePersistsInPortalAndNeverLosesLocalChoiceDuringTemporaryFailure()throws Exception{
        var exchange=mock(academy.llavor.academia.sso.I18nHttpClient.class);when(exchange.enabled()).thenReturn(true);exchange(exchange);var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);
        when(exchange.save(any(),eq("ca-ES-valencia"),eq(-1L))).thenThrow(new academy.llavor.academia.sso.SsoClientFailure(academy.llavor.academia.sso.SsoClientFailure.Code.REMOTE_UNAVAILABLE));
        bean.setSelectedLanguage("ca-ES-valencia");bean.change();assertEquals("ca-ES-valencia",bean.getLanguageTag());assertTrue(human.preferencePending());verify(session,never()).invalidate();
        when(exchange.save(any(),eq("ca-ES-valencia"),eq(-1L))).thenReturn(new academy.llavor.academia.sso.I18nHttpClient.Preference("ca-ES-valencia",0));bean.retryPreference();assertFalse(human.preferencePending());assertEquals(0,human.preferenceVersion());
    }
    @Test void renderingNeverContactsExchangeAndExplicitRefreshRequiresCsrf()throws Exception{
        var exchange=mock(academy.llavor.academia.sso.I18nHttpClient.class);exchange(exchange);for(int i=0;i<50;i++){bean.getText();bean.getLocale();}verifyNoInteractions(exchange);
        when(request.getParameter("languageCsrf")).thenReturn("FORGED");bean.refreshCatalog();verifyNoInteractions(exchange);
    }
    @Test void disabledExchangeRetainsPendingChoiceWithoutSendingSave()throws Exception{
        var exchange=mock(academy.llavor.academia.sso.I18nHttpClient.class);exchange(exchange);
        bean.setSelectedLanguage("ca-ES-valencia");bean.change();
        var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);
        assertTrue(human.preferencePending());assertEquals("ca-ES-valencia",human.language());
        verify(exchange,never()).save(any(),anyString(),anyLong());
    }
    @Test void conflictObservesCurrentVersionButRequiresExplicitRetryBeforeOverwriting()throws Exception{
        var exchange=mock(academy.llavor.academia.sso.I18nHttpClient.class);when(exchange.enabled()).thenReturn(true);exchange(exchange);
        when(exchange.save(any(),eq("ca-ES-valencia"),eq(-1L))).thenThrow(new academy.llavor.academia.sso.SsoClientFailure(academy.llavor.academia.sso.SsoClientFailure.Code.INVALID_REQUEST));
        when(exchange.read(any())).thenReturn(new academy.llavor.academia.sso.I18nHttpClient.Preference("es",8));
        bean.setSelectedLanguage("ca-ES-valencia");bean.change();var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);
        assertTrue(human.preferencePending());assertEquals("ca-ES-valencia",human.language());assertEquals(8,human.preferenceVersion());
        verify(exchange,times(1)).save(any(),anyString(),anyLong());
        when(exchange.save(any(),eq("ca-ES-valencia"),eq(8L))).thenReturn(new academy.llavor.academia.sso.I18nHttpClient.Preference("ca-ES-valencia",9));
        bean.retryPreference();assertFalse(human.preferencePending());assertEquals(9,human.preferenceVersion());
    }
    @Test void lostSaveResponseIsConfirmedOnlyWhenPortalReadMatchesSelection()throws Exception{
        var exchange=mock(academy.llavor.academia.sso.I18nHttpClient.class);when(exchange.enabled()).thenReturn(true);exchange(exchange);
        when(exchange.save(any(),anyString(),anyLong())).thenThrow(new academy.llavor.academia.sso.SsoClientFailure(academy.llavor.academia.sso.SsoClientFailure.Code.INVALID_REQUEST));
        when(exchange.read(any())).thenReturn(new academy.llavor.academia.sso.I18nHttpClient.Preference("ca-ES-valencia",2));
        bean.setSelectedLanguage("ca-ES-valencia");bean.change();var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);
        assertFalse(human.preferencePending());assertEquals(2,human.preferenceVersion());
    }
    @Test void latePreferenceResponsesCannotReplaceOrConfirmANewerSelection(){
        var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);
        human.restorePreference("ca-ES-valencia",5);human.restorePreference("es",3);
        assertEquals("ca-ES-valencia",human.language());assertEquals(5,human.preferenceVersion());
        human.chooseLanguage("es");human.preferenceVersionObserved(7);human.preferenceSaved("es",6);
        assertTrue(human.preferencePending());assertEquals(7,human.preferenceVersion());
        human.restorePreference("ca-ES-valencia",8);assertEquals("es",human.language());
        human.preferenceSaved("es",8);assertFalse(human.preferencePending());
    }
    @Test void previousPublicationKeepsValencianAndNewKeysUseSpanishUntilPublished(){
        var full=published(true);
        var oldMessages=new HashMap<String,Map<String,String>>();
        full.messages().forEach((tag,values)->{
            var previous=new HashMap<>(values);previous.keySet().removeIf(key->key.startsWith("academy.shell."));
            oldMessages.put(tag,previous);
        });
        when(catalogs.snapshot()).thenReturn(new CatalogSnapshot(1,full.portalInstanceId(),"ui",1,full.publishedAt(),full.languages(),oldMessages));
        var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);human.restorePreference("ca-ES-valencia",1);
        assertTrue(bean.isCanChange());assertEquals(Locale.forLanguageTag("ca-ES-valencia"),bean.getLocale());
        assertEquals("Acad\u00e8mia",bean.getText().get("academy.header.context"));
        assertEquals(AcademyInitialTexts.spanish().get("academy.shell.welcome"),bean.getText().get("academy.shell.welcome"));
        assertNotEquals(AcademyInitialTexts.entries().stream().filter(e->e.key().equals("academy.shell.welcome")).findFirst().orElseThrow().valencian(),bean.getText().get("academy.shell.welcome"));
    }
    @Test void restoreViewListenerAppliesTheEffectiveSessionLocale(){
        var human=(HumanSession)attributes.get(HumanSession.ATTRIBUTE);human.restorePreference("ca-ES-valencia",1);
        var application=mock(jakarta.faces.application.Application.class);when(faces.getApplication()).thenReturn(application);
        when(application.evaluateExpressionGet(faces,"#{academyLanguageBean}",AcademyLanguageBean.class)).thenReturn(bean);
        var listener=new academy.llavor.academia.i18n.web.AcademyLocalePhaseListener();
        assertEquals(jakarta.faces.event.PhaseId.RESTORE_VIEW,listener.getPhaseId());
        listener.afterPhase(new jakarta.faces.event.PhaseEvent(faces,jakarta.faces.event.PhaseId.RESTORE_VIEW,mock(jakarta.faces.lifecycle.Lifecycle.class)));
        verify(view).setLocale(Locale.forLanguageTag("ca-ES-valencia"));
    }

}
