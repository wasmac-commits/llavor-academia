package academy.llavor.academia.sso;

import jakarta.json.*;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import static academy.llavor.academia.sso.SsoClientFailure.Code.*;

/** Strict V1 decoding. Neither wire bodies nor parser messages escape this boundary. */
final class SsoResponseReader {
    private static final Set<String> CONSUME = Set.of("contractVersion","accessId","portalInstanceId","userId",
            "academyId","classroomId","accessExpiresAt","displayName","authorizedAt");
    private static final Set<String> CHECK = Set.of("contractVersion","accessId","portalInstanceId","userId",
            "academyId","classroomId","accessExpiresAt","valid","checkedAt");

    SsoAuthorization consumed(SsoWireResponse response, SsoSettings settings, Instant now) {
        Map<String,JsonValue> values = success(response, true);
        try {
            var fields=new HashSet<>(CONSUME);boolean i18n=values.containsKey("interfaceLanguage");if(i18n){fields.add("interfaceLanguage");fields.add("interfaceLanguageVersion");}exact(values,fields);version(values);
            String language=i18n?string(values,"interfaceLanguage"):"es";long languageVersion=-1;
            if(!academy.llavor.academia.i18n.core.LanguageTags.normalize(language).equals(language))throw invalid(true);
            if(i18n){if(!(values.get("interfaceLanguageVersion") instanceof JsonNumber n)||!n.isIntegral()||(languageVersion=n.longValueExact()) < -1)throw invalid(true);}
            UUID access=uuid(values,"accessId"); identities(values, settings);
            long user=user(values);
            Instant issued=time(values,"authorizedAt"), expires=time(values,"accessExpiresAt");
            String name=string(values,"displayName");
            if (name.isBlank() || name.codePointCount(0,name.length())>160 || name.chars().anyMatch(c->c<32)
                    || issued.isAfter(now.plusSeconds(5)) || !expires.isAfter(now) || !expires.isAfter(issued)
                    || expires.isAfter(issued.plus(Duration.ofHours(8)))) throw invalid(true);
            return new SsoAuthorization(access,settings.portalId(),user,settings.academyId(),
                    settings.classroomId(),issued,expires,name,language,languageVersion);
        } catch (RuntimeException e) { throw invalid(true); }
    }

    SsoRevalidation checked(SsoWireResponse response, SsoSettings settings, SsoAuthorization prior, Instant now) {
        Map<String,JsonValue> values=success(response, false);
        try {
            exact(values,CHECK); version(values); identities(values,settings);
            Instant checked=time(values,"checkedAt"), expiry=time(values,"accessExpiresAt");
            if (!uuid(values,"accessId").equals(prior.accessId()) || user(values)!=prior.userId()
                    || !expiry.equals(prior.accessExpiresAt()) || !expiry.isAfter(now)
                    || values.get("valid")!=JsonValue.TRUE || checked.isAfter(now.plusSeconds(5))
                    || checked.isBefore(now.minus(settings.requestTimeout()).minusSeconds(5))) throw invalid(false);
            return new SsoRevalidation(prior,checked);
        } catch (RuntimeException e) { throw invalid(false); }
    }

    void loggedOut(SsoWireResponse response){
        var values=success(response,false);
        exact(values,Set.of("contractVersion","revoked"));version(values);
        if(values.get("revoked")!=JsonValue.TRUE)throw invalid(false);
    }

    private Map<String,JsonValue> success(SsoWireResponse response, boolean consume) {
        if(response.status()>=300 && response.status()<400) throw new SsoClientFailure(REDIRECT_REJECTED,consume);
        Map<String,JsonValue> values;
        try {
            String contentType=response.header("Content-Type").toLowerCase(Locale.ROOT);
            if(!contentType.matches("application/json(?:\\s*;\\s*charset=\\s*(?:utf-8|\"utf-8\"))?"))
                throw invalid(consume);
            String encoding=response.header("Content-Encoding");
            if(response.headers().keySet().stream().anyMatch(k->k.equalsIgnoreCase("Content-Encoding"))
                    && !encoding.equalsIgnoreCase("identity")) throw invalid(consume);
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();
            values=object(text); version(values);
        }catch(Exception e){throw invalid(consume);}
        if(response.status()==200)return values;
        try{
            exact(values,Set.of("contractVersion","error"));
            JsonObject error=values.get("error").asJsonObject();
            if(!error.keySet().equals(Set.of("code")))throw invalid(consume);
            String code=error.getString("code");
            SsoClientFailure.Code failure=switch(response.status()){
                case 400 -> code.equals("INVALID_REQUEST")?INVALID_REQUEST:null;
                case 401 -> code.equals("AUTHENTICATION_FAILED")?AUTHENTICATION_REJECTED:null;
                case 403 -> Set.of("FORBIDDEN","ACCESS_UNAVAILABLE").contains(code)?ACCESS_DENIED:null;
                case 410 -> code.equals("TICKET_UNAVAILABLE")?TICKET_UNAVAILABLE:null;
                case 503 -> code.equals("SERVICE_UNAVAILABLE")?REMOTE_UNAVAILABLE:null;
                default -> null;
            };
            if(failure==null)throw invalid(consume);
            throw new SsoClientFailure(failure,consume && response.status()==503);
        }catch(SsoClientFailure e){throw e;}
        catch(RuntimeException e){throw invalid(consume);}
    }

    /** Streaming first pass rejects duplicate names at every depth, trailing documents and excessive nesting. */
    static Map<String,JsonValue> object(String text) {
        if(text.isEmpty() || text.charAt(0)=='\ufeff')throw invalid(false);
        Deque<Set<String>> names=new ArrayDeque<>(); int roots=0, depth=0;
        try(JsonParser parser=Json.createParser(new StringReader(text))){
            while(parser.hasNext()){
                var event=parser.next();
                switch(event){
                    case START_OBJECT,START_ARRAY -> {
                        if(depth++==0){if(event!=JsonParser.Event.START_OBJECT || ++roots!=1)throw invalid(false);}
                        if(depth>4)throw invalid(false);
                        names.push(new HashSet<>());
                    }
                    case END_OBJECT,END_ARRAY -> { depth--;names.pop(); }
                    case KEY_NAME -> {if(!names.peek().add(parser.getString()))throw invalid(false);}
                    default -> {if(depth==0)throw invalid(false);}
                }
            }
        }
        if(roots!=1 || depth!=0)throw invalid(false);
        try(var reader=Json.createReader(new StringReader(text))){
            return new HashMap<>(reader.readObject());
        }
    }
    private static void exact(Map<String,JsonValue> values,Set<String> fields){if(!values.keySet().equals(fields))throw invalid(false);}
    private static void version(Map<String,JsonValue> values){
        if(!(values.get("contractVersion") instanceof JsonNumber n) || !n.toString().equals("1"))throw invalid(false);
    }
    private static String string(Map<String,JsonValue> v,String k){
        if(!(v.get(k) instanceof JsonString s))throw invalid(false);return s.getString();
    }
    private static UUID uuid(Map<String,JsonValue> v,String k){return SsoValidation.uuid(string(v,k));}
    private static long user(Map<String,JsonValue> v){
        String value=string(v,"userId");if(!value.matches("[1-9][0-9]{0,18}"))throw invalid(false);
        return Long.parseLong(value);
    }
    private static Instant time(Map<String,JsonValue> v,String k){
        String text=string(v,k);
        Instant value=Instant.parse(text);
        if(!value.toString().equals(text) || value.getNano()%1000!=0 || !text.matches("[1-9][0-9]{3}-.*Z"))throw invalid(false);
        return value;
    }
    private static void identities(Map<String,JsonValue> v,SsoSettings settings){
        if(!uuid(v,"portalInstanceId").equals(settings.portalId()) || !uuid(v,"academyId").equals(settings.academyId())
                || !uuid(v,"classroomId").equals(settings.classroomId()))throw invalid(false);
    }
    private static SsoClientFailure invalid(boolean uncertain){return new SsoClientFailure(INVALID_RESPONSE,uncertain);}
}
