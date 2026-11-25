package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.SsoAuthorization;
import java.io.Serializable;
import java.net.URI;
import java.time.*;
/** Human-only session. No ticket, binding secret, technical credential, envelope or global role. */
public final class HumanSession implements Serializable {
    public static final String ATTRIBUTE=HumanSession.class.getName();
    private final SsoAuthorization authorization;private final Instant establishedAt,expiresAt;private final String csrf;private final URI academyBaseUrl;
    private Instant lastSeen;
    private String interfaceLanguage;
    private long preferenceVersion;
    private boolean preferencePending;
    HumanSession(SsoAuthorization authorization,Instant now,String csrf,URI academyBaseUrl){
        if(authorization==null||authorization.userId()<1||authorization.displayName()==null||authorization.displayName().isBlank()
            ||authorization.authorizedAt()==null||authorization.authorizedAt().isAfter(now.plusSeconds(5))
            ||authorization.accessExpiresAt()==null||!authorization.accessExpiresAt().isAfter(now))throw new BrowserFailure("INVALID_GRANT");
        this.authorization=authorization;this.establishedAt=now;this.lastSeen=now;this.csrf=csrf;this.academyBaseUrl=academyBaseUrl;
        interfaceLanguage=academy.llavor.academia.i18n.core.LanguageTags.normalize(authorization.interfaceLanguage());preferenceVersion=authorization.interfaceLanguageVersion();
        Instant max=now.plus(Duration.ofHours(8));this.expiresAt=authorization.accessExpiresAt().isBefore(max)?authorization.accessExpiresAt():max;
    }
    public SsoAuthorization authorization(){return authorization;}
    public URI academyBaseUrl(){return academyBaseUrl;}
    public String csrf(){return csrf;}
    synchronized boolean valid(Instant now){return !now.isBefore(establishedAt)&&now.isBefore(expiresAt)&&now.isBefore(lastSeen.plusSeconds(1800));}
    synchronized void touch(Instant now){if(now.isAfter(lastSeen))lastSeen=now;}
    public Instant expiresAt(){return expiresAt;}
    public synchronized String language(){return interfaceLanguage==null?"es":interfaceLanguage;}
    public synchronized long preferenceVersion(){return preferenceVersion;}
    public synchronized boolean preferencePending(){return preferencePending;}
    public synchronized void chooseLanguage(String tag){interfaceLanguage=academy.llavor.academia.i18n.core.LanguageTags.normalize(tag);preferencePending=true;}
    public synchronized void preferenceSaved(String tag,long version){if(version< -1)throw new IllegalArgumentException();if(version<preferenceVersion)return;preferenceVersion=version;if(language().equals(tag))preferencePending=false;}
    public synchronized void restorePreference(String tag,long version){if(version< -1)throw new IllegalArgumentException();if(!preferencePending&&version>=preferenceVersion){interfaceLanguage=academy.llavor.academia.i18n.core.LanguageTags.normalize(tag);preferenceVersion=version;}}
    public synchronized void preferenceVersionObserved(long version){if(version< -1)throw new IllegalArgumentException();preferenceVersion=Math.max(preferenceVersion,version);}
    public String toString(){return "HumanSession[redacted]";}
}
