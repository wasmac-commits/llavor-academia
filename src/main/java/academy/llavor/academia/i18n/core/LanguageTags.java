package academy.llavor.academia.i18n.core;
import java.util.Locale;
/** BCP 47 syntax, no extensions/private use; variant case is canonicalized explicitly on JDK 21. */
public final class LanguageTags {
    public static final String BASE="es", VALENCIAN="ca-ES-valencia";
    private LanguageTags(){}
    public static String normalize(String tag){
        if(tag==null||tag.length()>63||!tag.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*"))throw new IllegalArgumentException("Invalid language tag");
        Locale locale;try{locale=new Locale.Builder().setLanguageTag(tag).build();}
        catch(java.util.IllformedLocaleException e){throw new IllegalArgumentException("Invalid language tag");}
        if(locale.hasExtensions()||locale.getLanguage().isEmpty()||locale.getLanguage().equals("und"))throw new IllegalArgumentException("Unsupported language tag");
        return new Locale.Builder().setLocale(locale).setVariant(locale.getVariant().toLowerCase(Locale.ROOT)).build().toLanguageTag();
    }
    public static String key(String key){
        if(key==null||key.length()>180||!key.matches("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)+"))throw new IllegalArgumentException("Invalid text key");
        return key;
    }
}
