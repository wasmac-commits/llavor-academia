package academy.llavor.academia.sso;
import java.time.Instant;
/** Exists only after a fresh positive remote check; no offline/cached authorization. */
public record SsoRevalidation(SsoAuthorization authorization,Instant checkedAt) {}
