package academy.llavor.academia.provisioning.validation;

import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure model checks: never reads configuration, secrets, network or a database. */
public final class ProvisioningValidation {
    public static final int CONTRACT_VERSION = 1;
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private ProvisioningValidation() {}

    public static UUID uuid(UUID value) {
        if (value == null || (value.getMostSignificantBits() == 0 && value.getLeastSignificantBits() == 0))
            throw new IllegalArgumentException("Se requiere una identidad UUID no nula.");
        return value;
    }
    public static UUID parseUuid(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches())
            throw new IllegalArgumentException("El UUID debe tener formato canonico en minusculas.");
        return uuid(UUID.fromString(value));
    }
    public static String fingerprint(String value) {
        if (value == null || !HASH.matcher(value).matches())
            throw new IllegalArgumentException("La huella SHA-256 debe contener 64 caracteres hexadecimales en minusculas.");
        return value;
    }
    public static int contractVersion(int value) {
        if (value != CONTRACT_VERSION) throw new IllegalArgumentException("Version de contrato no admitida.");
        return value;
    }
    public static String code(String value) {
        if (value == null || !value.strip().matches("[A-Za-z0-9_]{1,80}"))
            throw new IllegalArgumentException("El codigo admite de 1 a 80 letras ASCII, numeros o guion bajo.");
        return value.strip().toUpperCase(Locale.ROOT);
    }
    public static String name(String value) {
        if (value == null) throw new IllegalArgumentException("El nombre es obligatorio.");
        int start = 0, end = value.length();
        while (start < end && space(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (start < end && space(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        String text = value.substring(start, end);
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 190)
            throw new IllegalArgumentException("El nombre es obligatorio y admite hasta 190 caracteres.");
        return text;
    }
    private static boolean space(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point) || point == 0x85;
    }
    public static String claimant(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,190}"))
            throw new IllegalArgumentException("Identificador de trabajador no valido.");
        return value;
    }
}
