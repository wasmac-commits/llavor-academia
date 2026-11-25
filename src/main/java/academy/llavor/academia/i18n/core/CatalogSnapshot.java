package academy.llavor.academia.i18n.core;
import java.time.Instant;
import java.util.*;
/** Complete immutable publication: enabled languages only, full Spanish base and sparse translations. */
public record CatalogSnapshot(int formatVersion,UUID portalInstanceId,String catalogCode,long version,
        Instant publishedAt,Map<String,String> languages,Map<String,Map<String,String>> messages) {
    public CatalogSnapshot {
        if(formatVersion!=1||portalInstanceId==null||portalInstanceId.equals(new UUID(0,0))||catalogCode==null
            ||!catalogCode.matches("[a-z][a-z0-9_-]{0,39}")||version<0||publishedAt==null||(version==0&&!Instant.EPOCH.equals(publishedAt))||publishedAt.isBefore(Instant.EPOCH))throw new IllegalArgumentException("Invalid catalog identity");
        if(languages==null||languages.isEmpty()||languages.size()>32||!languages.containsKey("es")||messages==null||!messages.keySet().equals(languages.keySet()))throw new IllegalArgumentException("Invalid catalog languages");
        var names=new TreeMap<String,String>();
        languages.forEach((tag,name)->{
            if(!LanguageTags.normalize(tag).equals(tag)||name==null||name.isBlank()||name.length()>120||!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(name)||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid language");
            names.put(tag,name);
        });languages=Collections.unmodifiableMap(names);
        Map<String,String> base=messages.get("es");
        if(base==null||base.isEmpty()||base.size()>10000)throw new IllegalArgumentException("Missing Spanish base");
        var copy=new TreeMap<String,Map<String,String>>();
        for(var entry:messages.entrySet()){
            if(entry.getValue()==null||entry.getValue().size()>10000)throw new IllegalArgumentException("Invalid messages");
            var texts=new TreeMap<String,String>();
            entry.getValue().forEach((key,pattern)->{
                LanguageTags.key(key);
                if(!base.containsKey(key)||!TextTemplate.arguments(pattern).equals(TextTemplate.arguments(base.get(key))))throw new IllegalArgumentException("Incompatible translation");
                texts.put(key,pattern);
            });copy.put(entry.getKey(),Collections.unmodifiableMap(texts));
        }
        messages=Collections.unmodifiableMap(copy);
    }
    public String effectiveLanguage(String requested){
        try{String tag=LanguageTags.normalize(requested);return languages.containsKey(tag)?tag:"es";}
        catch(IllegalArgumentException e){return "es";}
    }
    public Resolution resolve(String requested,String key,Object... args){
        LanguageTags.key(key);String language=effectiveLanguage(requested);
        String normalized;try{normalized=LanguageTags.normalize(requested);}catch(IllegalArgumentException e){normalized=null;}
        String pattern=messages.get(language).get(key);boolean fallback=!Objects.equals(normalized,language);
        if(pattern==null){pattern=messages.get("es").get(key);fallback=true;}
        if(pattern==null)return new Resolution("["+key+"]","es",true,true);
        return new Resolution(TextTemplate.format(pattern,args),fallback?"es":language,fallback,false);
    }
    public record Resolution(String text,String language,boolean fallback,boolean missing){}
}
