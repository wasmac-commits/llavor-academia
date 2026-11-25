package academy.llavor.academia.i18n.web;

import academy.llavor.academia.i18n.core.*;
import academy.llavor.academia.i18n.seed.AcademyInitialTexts;
import academy.llavor.academia.i18n.service.LocalTranslationService;
import academy.llavor.academia.sso.browser.HumanSession;
import academy.llavor.academia.sso.I18nHttpClient;
import academy.llavor.academia.sso.SsoClientFailure;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Logger;
import java.util.*;

/**
 * Request-local rendering from the last installed catalog. No Portal client or
 * remote preference calls.
 */
@Named
@RequestScoped
public class AcademyLanguageBean {
    private static final String CHOICE = AcademyLanguageBean.class.getName() + ".choice";
    private static final Logger LOG = Logger.getLogger(AcademyLanguageBean.class.getName());
    private LocalTranslationService catalogs;
    private FacesContext faces;
    @Inject
    private I18nHttpClient exchange;
    private boolean loaded;
    private CatalogSnapshot snapshot;
    private String effective = "es", selected = "es";
    private List<Option> languages = List.of();
    private Map<String, String> texts;

    protected AcademyLanguageBean() {
    }

    @Inject
    public AcademyLanguageBean(LocalTranslationService catalogs, FacesContext faces) {
        this.catalogs = catalogs;
        this.faces = faces;
    }

    private HttpServletRequest request() {
        Object request = faces.getExternalContext().getRequest();
        return request instanceof HttpServletRequest http ? http : null;
    }

    private void load() {
        if (loaded)
            return;
        loaded = true;
        try {
            snapshot = catalogs.snapshot();
        } catch (RuntimeException unavailable) {
            snapshot = null;
        }
        // Preserve existing published languages. Stage 21 additions use the existing Spanish
        // recovery source until reviewed/published centrally; never render unpublished Valencian.
        boolean ready = snapshot != null && snapshot.version() > 0
                && snapshot.messages().get("es").keySet().containsAll(AcademyInitialTexts.spanish().keySet().stream().filter(key -> !key.startsWith("academy.shell.")).toList());
        var names = ready ? snapshot.languages()
                : Map.of("es", snapshot == null ? AcademyInitialTexts.baseName() : snapshot.languages().get("es"));
        languages = names.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, String>, Boolean>comparing(e -> !e.getKey().equals("es"))
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new Option(e.getKey(), e.getValue())).toList();
        var http = request();
        var session = http == null ? null : http.getSession(false);
        Object choice = session == null ? null : session.getAttribute(CHOICE);
        Object human = session == null ? null : session.getAttribute(HumanSession.ATTRIBUTE);
        if (human instanceof HumanSession authenticated && names.containsKey(authenticated.language()))
            effective = authenticated.language();
        else if (choice instanceof String tag && names.containsKey(tag))
            effective = tag;
        selected = effective;
    }

    public Map<String, String> getText() {
        load();
        if (texts == null) {
            var result = new HashMap<>(AcademyInitialTexts.spanish());
            if (snapshot != null) {
                result.putAll(snapshot.messages().get("es"));
                if (!effective.equals("es"))
                    result.putAll(snapshot.messages().get(effective));
            }
            texts = Collections.unmodifiableMap(result);
        }
        return texts;
    }

    public String format(String key, Object argument) {
        String pattern = getText().get(key);
        return pattern == null ? "[" + key + "]" : TextTemplate.format(pattern, argument);
    }

    public void change() throws IOException {
        load();
        var http = request();
        var session = http == null ? null : http.getSession(false);
        Object human = session == null ? null : session.getAttribute(HumanSession.ATTRIBUTE);
        String token = http == null ? null : http.getParameter("languageCsrf");
        if (http == null || !"POST".equals(http.getMethod()) || !(human instanceof HumanSession authenticated)
                || token == null
                || !MessageDigest.isEqual(authenticated.csrf().getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            fail("portal.language.invalid_request");
            return;
        }
        String tag;
        try {
            tag = LanguageTags.normalize(selected);
        } catch (IllegalArgumentException invalid) {
            fail("portal.language.unavailable");
            return;
        }
        if (languages.stream().noneMatch(option -> option.tag().equals(tag))) {
            fail("portal.language.unavailable");
            return;
        }
        // Local choice applies even when its explicit remote persistence temporarily
        // fails.
        authenticated.chooseLanguage(tag);
        persistPreference(authenticated, tag);
        session.setAttribute(CHOICE, tag);
        effective = tag;
        selected = tag;
        texts = null;
        faces.getViewRoot().setLocale(Locale.forLanguageTag(tag));
        faces.getExternalContext().redirect(faces.getExternalContext().getRequestContextPath() + "/index.xhtml");
        faces.responseComplete();
    }

    private HumanSession actionHuman() {
        var http = request();
        var session = http == null ? null : http.getSession(false);
        Object value = session == null ? null : session.getAttribute(HumanSession.ATTRIBUTE);
        String token = http == null ? null : http.getParameter("languageCsrf");
        if (http == null || !"POST".equals(http.getMethod()) || !(value instanceof HumanSession human) || token == null
                || !MessageDigest.isEqual(human.csrf().getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            fail("portal.language.invalid_request");
            return null;
        }
        return human;
    }

    private void persistPreference(HumanSession human, String tag) {
        if (exchange == null || !exchange.enabled()) {
            LOG.warning("I18N preference: exchange disabled");
            warn("academy.i18n.pending");
            return;
        }
        try {
            var saved = exchange.save(human.authorization(), tag, human.preferenceVersion());
            human.preferenceSaved(saved.language(), saved.version());
            LOG.info("I18N preference: save completed, pending=" + human.preferencePending());
        } catch (SsoClientFailure conflict) {
            LOG.warning("I18N preference: save failed, code=" + conflict.getCode());
            if (conflict.getCode() == SsoClientFailure.Code.INVALID_REQUEST) {
                try {
                    var current = exchange.read(human.authorization());
                    human.preferenceVersionObserved(current.version());
                    if (current.language().equals(tag))
                        human.preferenceSaved(tag, current.version());
                } catch (RuntimeException unavailable) {
                    LOG.warning("I18N preference: read after conflict failed, type="
                            + unavailable.getClass().getSimpleName());
                }
            }
            if (human.preferencePending())
                warn("academy.i18n.pending");
        } catch (RuntimeException unavailable) {
            LOG.warning("I18N preference: unexpected failure, type=" + unavailable.getClass().getSimpleName());
            warn("academy.i18n.pending");
        }
    }

    public void retryPreference() throws IOException {
        load();
        var human = actionHuman();
        if (human == null)
            return;
        if (languages.stream().noneMatch(option -> option.tag().equals(human.language()))) {
            fail("portal.language.unavailable");
            return;
        }
        persistPreference(human, human.language());
        faces.getExternalContext().redirect(faces.getExternalContext().getRequestContextPath() + "/index.xhtml");
        faces.responseComplete();
    }

    public void refreshCatalog() throws IOException {
        load();
        var human = actionHuman();
        if (human == null)
            return;
        if (exchange == null || !exchange.enabled()) {
            fail("academy.i18n.sync_failed");
            return;
        }
        try {
            exchange.refresh(human.authorization());
            if (!human.preferencePending()) {
                var preference = exchange.read(human.authorization());
                human.restorePreference(preference.language(), preference.version());
            }
            faces.getExternalContext().redirect(faces.getExternalContext().getRequestContextPath() + "/index.xhtml");
            faces.responseComplete();
        } catch (RuntimeException unavailable) {
            fail("academy.i18n.sync_failed");
        }
    }

    private void warn(String key) {
        var flash = faces.getExternalContext().getFlash();
        if (flash != null)
            flash.setKeepMessages(true);
        faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, getText().get(key), null));
    }

    public boolean isExchangeEnabled() {
        return exchange != null && exchange.enabled();
    }

    public boolean isPreferencePending() {
        var http = request();
        var session = http == null ? null : http.getSession(false);
        return session != null && session.getAttribute(HumanSession.ATTRIBUTE) instanceof HumanSession human
                && human.preferencePending();
    }

    private void fail(String key) {
        selected = effective;
        faces.validationFailed();
        faces.addMessage("academyLocaleForm:locale",
                new FacesMessage(FacesMessage.SEVERITY_ERROR, getText().get(key), null));
    }

    public Locale getLocale() {
        load();
        return Locale.forLanguageTag(effective);
    }

    public String getLanguageTag() {
        load();
        return effective;
    }

    public List<Option> getLanguages() {
        load();
        return languages;
    }

    public String getSelectedLanguage() {
        load();
        return selected;
    }

    public void setSelectedLanguage(String value) {
        load();
        selected = value;
    }

    public boolean isCanChange() {
        load();
        return languages.size() > 1;
    }

    public record Option(String tag, String nativeName) {
        public String getTag() {
            return tag;
        }

        public String getNativeName() {
            return nativeName;
        }
    }
}
