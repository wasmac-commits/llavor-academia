package academy.llavor.academia.i18n.seed;
import academy.llavor.academia.i18n.core.*;
import jakarta.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Reviewed seed data and Spanish recovery source. Never installs data or renders unpublished Valencian. */
public final class AcademyInitialTexts {
    private AcademyInitialTexts(){}
    public record Entry(String key,String spanish,String valencian,String description){}
    private record Data(List<Entry> entries,Map<String,String> spanish,String baseName){}
    private static final class Holder {static final Data DATA=load();}
    public static List<Entry> entries(){return Holder.DATA.entries();}
    public static Map<String,String> spanish(){return Holder.DATA.spanish();}
    public static String baseName(){return Holder.DATA.baseName();}
    private static Data load(){
        try(var stream=AcademyInitialTexts.class.getResourceAsStream("/i18n/academy-initial-texts.json")){
            if(stream==null)throw new IllegalStateException("Missing initial Portal texts");
            byte[] bytes=stream.readNBytes(CatalogCodec.MAX_BYTES+1);if(bytes.length>CatalogCodec.MAX_BYTES)throw new IllegalArgumentException();
            JsonObject json;try(var reader=Json.createReader(new StringReader(new String(bytes,StandardCharsets.UTF_8)))){json=reader.readObject();}
            if(json.getInt("formatVersion")!=1||!json.keySet().equals(Set.of("formatVersion","languages","texts")))throw new IllegalArgumentException();
            var entries=new ArrayList<Entry>();var spanish=new TreeMap<String,String>();
            for(var value:json.getJsonArray("texts")){
                var row=value.asJsonObject();if(!row.keySet().equals(Set.of("key","es","ca-ES-valencia","description")))throw new IllegalArgumentException();
                var entry=new Entry(LanguageTags.key(row.getString("key")),row.getString("es"),row.getString("ca-ES-valencia"),row.getString("description"));
                if(!TextTemplate.arguments(entry.spanish()).equals(TextTemplate.arguments(entry.valencian()))||entry.description().length()>500
                    ||spanish.putIfAbsent(entry.key(),entry.spanish())!=null)throw new IllegalArgumentException("Invalid initial text");
                entries.add(entry);
            }
            if(entries.isEmpty())throw new IllegalArgumentException();
            return new Data(List.copyOf(entries),Collections.unmodifiableMap(spanish),json.getJsonObject("languages").getString("es"));
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Initial Portal texts are not valid",failure);}
    }
}
