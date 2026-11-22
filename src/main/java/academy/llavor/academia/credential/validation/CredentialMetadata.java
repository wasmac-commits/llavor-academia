package academy.llavor.academia.credential.validation;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Nonsecret administrative metadata only. No persistence, network or cryptographic operations. */
public final class CredentialMetadata {
    private CredentialMetadata() {}
    public static UUID operation(UUID value) {
        if (value == null || value.version() != 4 || value.variant() != 2) throw new IllegalArgumentException("Operacion UUID v4 obligatoria.");
        return value;
    }
    public static String keyId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Identificador de clave no valido.");
        return value;
    }
    public static String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Huella SHA-256 canonica obligatoria.");
        return value;
    }
    public static LocalDateTime utc(Instant value) {
        if (value == null) throw new IllegalArgumentException("Fecha UTC obligatoria.");
        var time=LocalDateTime.ofInstant(value,ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (time.getYear()<1000 || time.getYear()>9999) throw new IllegalArgumentException("Fecha fuera de DATETIME.");
        return time;
    }
    public static void revision(int value) { if(value<1) throw new IllegalArgumentException("Revision positiva obligatoria."); }
}
