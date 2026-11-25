package academy.llavor.academia.i18n.core;
import java.util.*;
/** Plain text only. Numbered {0}..{19}, literal braces {{ and }}; apostrophes have no special meaning. */
public final class TextTemplate {
    private TextTemplate(){}
    public static Set<Integer> arguments(String pattern){return parse(pattern,null,new StringBuilder());}
    public static String format(String pattern,Object... args){
        if(args==null)throw new IllegalArgumentException("Missing arguments");
        Set<Integer> expected=arguments(pattern);
        if((expected.isEmpty()?0:Collections.max(expected)+1)!=args.length)throw new IllegalArgumentException("Argument count mismatch");
        for(Object arg:args)if(!(arg instanceof String||arg instanceof Number||arg instanceof Boolean))throw new IllegalArgumentException("Plain scalar arguments required");
        var out=new StringBuilder();parse(pattern,args,out);return out.toString();
    }
    private static Set<Integer> parse(String text,Object[] args,StringBuilder out){
        if(text==null||text.isBlank()||text.length()>2000||!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text)||text.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IllegalArgumentException("Invalid text template");
        Set<Integer> found=new TreeSet<>();
        for(int i=0;i<text.length();){
            char c=text.charAt(i++);
            if(c=='{'||c=='}'){
                if(i<text.length()&&text.charAt(i)==c){out.append(c);i++;continue;}
                if(c=='}')throw new IllegalArgumentException("Unbalanced template");
                int end=text.indexOf('}',i);if(end<0)throw new IllegalArgumentException("Unbalanced template");
                String index=text.substring(i,end);if(!index.matches("[0-9]|1[0-9]"))throw new IllegalArgumentException("Invalid placeholder");
                int n=Integer.parseInt(index);found.add(n);if(args!=null)out.append(args[n]);i=end+1;
            }else out.append(c);
        }
        return Set.copyOf(found);
    }
}
