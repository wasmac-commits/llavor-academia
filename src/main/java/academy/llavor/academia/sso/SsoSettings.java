package academy.llavor.academia.sso;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
/** Nonsecret request snapshot. Does not contain a credential or provisioning switches. */
public record SsoSettings(UUID academyId,UUID portalId,UUID classroomId,URI portalBaseUrl,String keyId,
        Duration connectTimeout,Duration requestTimeout) {}
