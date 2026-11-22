package academy.llavor.academia.parameter.validation;

import academy.llavor.academia.parameter.entity.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Pure validation. No configuration access, cryptography, networking or database writes. */
public final class ParameterValidation {
    public static final int VALUE_LIMIT = 4000;
    private ParameterValidation() {}
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Parametro no valido para su tipo o politica."); }
    public static String text(String input,int limit,boolean required) {
        if(input==null) { if(required)throw invalid(); return null; }
        String result=input.strip();
        if(result.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0x85)) {
            if(required)throw invalid();return null;
        }
        if(result.codePointCount(0,result.length())>limit || result.indexOf(0)>=0)throw invalid();
        return result;
    }
    public static String key(String input) {
        if(input==null || !input.matches("[a-z][A-Za-z0-9_.-]{0,119}"))throw invalid();
        return input;
    }
    public static boolean protectedKey(String key) {
        return key.startsWith("llavor.portal.provisioning.") || key.startsWith("llavor.academia.")
            || key.equals("llavor.portal.expected-instance-id");
    }
    public static boolean secretType(ParameterType type) {
        return type==ParameterType.ENCRYPTED_SECRET || type==ParameterType.SHA256_VERIFIER;
    }
    public static void catalogueType(String key,ParameterType type) {
        if(Set.of("llavor.portal.expected-instance-id","llavor.academia.instance-id","llavor.academia.code","llavor.academia.portal-instance-id").contains(key))
            throw new IllegalArgumentException("Las identidades no pertenecen al catalogo generico.");
        if(!protectedKey(key))return;
        ParameterType required=null;
        if(key.endsWith(".credential"))required=ParameterType.ENCRYPTED_SECRET;
        else if(key.endsWith(".verifier"))required=ParameterType.SHA256_VERIFIER;
        else if(key.endsWith(".enabled")||key.endsWith(".allow-loopback-http"))required=ParameterType.BOOLEAN;
        else if(key.endsWith(".allowed-base-url"))required=ParameterType.URL;
        else if(key.endsWith("-timeout-seconds")||key.endsWith(".lease-seconds")||key.endsWith(".batch-size")||key.endsWith(".max-attempts"))required=ParameterType.INTEGER;
        if(required!=null&&type!=required)throw invalid();
    }
    public static UUID uuid(UUID uuid) {
        if(uuid==null || uuid.equals(new UUID(0,0)))throw invalid(); return uuid;
    }
    public static String value(ParameterType type,String input) {
        if(type==null)throw invalid();
        if(input==null)return null; // An unset value is not a usable credential/configuration.
        if(input.codePointCount(0,input.length())>VALUE_LIMIT || input.indexOf(0)>=0)throw invalid();
        try {
            switch(type) {
                case TEXT -> { return input; }
                case BOOLEAN -> { if(!input.equals("true")&&!input.equals("false"))throw invalid(); }
                case INTEGER -> {
                    if(!input.matches("0|-?[1-9][0-9]{0,18}"))throw invalid();
                    Long.parseLong(input);
                }
                case DECIMAL -> {
                    if(!input.matches("-?(0|[1-9][0-9]{0,14})([.][0-9]{0,3}[1-9])?") || input.equals("-0"))throw invalid();
                    if(!new BigDecimal(input).stripTrailingZeros().toPlainString().equals(input))throw invalid();
                }
                case DATE -> {
                    if(!input.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}"))throw invalid();
                    LocalDate.parse(input);
                }
                case DATETIME -> {
                    if(!input.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]{1,6})?Z"))throw invalid();
                    OffsetDateTime.parse(input);
                }
                case UUID -> {
                    if(!input.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid();
                    uuid(UUID.fromString(input));
                }
                case EMAIL -> { if(input.length()>254 || !input.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)+"))throw invalid(); }
                case URL -> {
                    if(input.length()>500 || !input.matches("[!-~]+"))throw invalid();
                    URI uri=new URI(input);uri.parseServerAuthority();
                    if(!uri.isAbsolute() || uri.isOpaque() || uri.getHost()==null
                        || !("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))
                        || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                        || uri.getPort()==0 || uri.getPort()>65535 || uri.getRawAuthority().endsWith(":"))throw invalid();
                    if(!uri.getHost().equals(uri.getHost().toLowerCase(Locale.ROOT)) || input.endsWith("/"))throw invalid();
                }
                case SHA256_VERIFIER -> { if(!input.matches("[0-9a-f]{64}"))throw invalid(); }
                case ENCRYPTED_SECRET -> {
                    // Envelope only: v1.<masterKeyId>.<96-bit-IV>.<ciphertext-with-128-bit-tag>.
                    // Actual AES-GCM authentication/AAD checks belong to the future crypto adapter.
                    String[] parts=input.split("[.]",-1);
                    if(parts.length!=4 || !parts[0].equals("v1") || !parts[1].matches("[A-Za-z0-9_-]{1,32}"))throw invalid();
                    if(canonicalBase64(parts[2]).length!=12)throw invalid();
                    int bytes=canonicalBase64(parts[3]).length;
                    if(bytes<17 || bytes>2048)throw invalid();
                }
            }
            return input;
        } catch(Exception ignored) { throw invalid(); } // Never echo the value or original cause.
    }
    private static byte[] canonicalBase64(String value) {
        if(!value.matches("[A-Za-z0-9_-]+"))throw invalid();
        byte[] bytes=Base64.getUrlDecoder().decode(value);
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value))throw invalid();
        return bytes;
    }
}
