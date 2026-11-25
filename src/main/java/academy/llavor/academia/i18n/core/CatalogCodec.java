package academy.llavor.academia.i18n.core;
import jakarta.json.*;
import jakarta.json.stream.JsonParser;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
/** Deterministic bounded JSON contract; transport authentication belongs to a future adapter. */
public final class CatalogCodec {
    public static final int MAX_BYTES=4*1024*1024;
    private CatalogCodec(){}
    public static String encode(CatalogSnapshot snapshot){
        var names=Json.createObjectBuilder();snapshot.languages().forEach(names::add);
        var messages=Json.createObjectBuilder();snapshot.messages().forEach((tag,texts)->{
            var items=Json.createObjectBuilder();texts.forEach(items::add);messages.add(tag,items);
        });
        String json=Json.createObjectBuilder().add("formatVersion",1).add("portalInstanceId",snapshot.portalInstanceId().toString())
            .add("catalogCode",snapshot.catalogCode()).add("version",snapshot.version()).add("publishedAt",snapshot.publishedAt().toString())
            .add("languages",names).add("messages",messages).build().toString();
        if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Catalog too large");return json;
    }
    public static String digest(String json){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static CatalogSnapshot decode(byte[] bytes,String sha256){
        if(bytes==null||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Catalog too large");
        try{return decode(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(),sha256);}
        catch(CharacterCodingException e){throw new IllegalArgumentException("Invalid UTF-8 catalog");}
    }
    public static CatalogSnapshot decode(String json,String sha256){
        if(json==null||json.length()>MAX_BYTES||json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES
            ||sha256==null||!sha256.matches("[0-9a-f]{64}")||!digest(json).equals(sha256))throw new IllegalArgumentException("Invalid catalog payload");
        try{
            // JsonReader alone accepts duplicate properties. Reject them before materializing the object.
            Deque<Set<String>> stack=new ArrayDeque<>();int roots=0;
            try(var parser=Json.createParser(new StringReader(json))){
                while(parser.hasNext())switch(parser.next()){
                    case START_OBJECT -> {if(stack.isEmpty()&&++roots!=1)throw new IllegalArgumentException();if(stack.size()>=4)throw new IllegalArgumentException();stack.push(new HashSet<>());}
                    case END_OBJECT -> stack.pop();
                    case KEY_NAME -> {if(!stack.peek().add(parser.getString()))throw new IllegalArgumentException();}
                    case START_ARRAY,END_ARRAY -> throw new IllegalArgumentException();
                    default -> {if(stack.isEmpty())throw new IllegalArgumentException();}
                }
            }
            if(roots!=1||!stack.isEmpty())throw new IllegalArgumentException();
            JsonObject object;try(var reader=Json.createReader(new StringReader(json))){object=reader.readObject();}
            if(!object.keySet().equals(Set.of("formatVersion","portalInstanceId","catalogCode","version","publishedAt","languages","messages")))throw new IllegalArgumentException();
            UUID portal=UUID.fromString(object.getString("portalInstanceId"));if(!portal.toString().equals(object.getString("portalInstanceId")))throw new IllegalArgumentException();
            var names=new TreeMap<String,String>();object.getJsonObject("languages").forEach((k,v)->names.put(k,((JsonString)v).getString()));
            Map<String,Map<String,String>> texts=new TreeMap<>();object.getJsonObject("messages").forEach((tag,value)->{
                Map<String,String> items=new TreeMap<>();value.asJsonObject().forEach((k,v)->items.put(k,((JsonString)v).getString()));texts.put(tag,items);
            });
            return new CatalogSnapshot(object.getJsonNumber("formatVersion").intValueExact(),portal,object.getString("catalogCode"),
                object.getJsonNumber("version").longValueExact(),Instant.parse(object.getString("publishedAt")),names,texts);
        }catch(RuntimeException e){throw new IllegalArgumentException("Invalid catalog payload");}
    }
}
