package academy.llavor.academia.parameter.dto;
import academy.llavor.academia.parameter.entity.*;
public record ParameterFilter(String text,ParameterGroup group,ParameterType type,Boolean active) {
 public static final ParameterFilter ALL=new ParameterFilter(null,null,null,null);
 public ParameterFilter { if(text!=null){text=text.strip();if(text.isEmpty())text=null;else if(text.length()>190)throw new IllegalArgumentException("Filtro no valido.");} }
 public static void page(int first,int size){if(first<0||size<1||size>100)throw new IllegalArgumentException("Pagina no valida.");}
}
