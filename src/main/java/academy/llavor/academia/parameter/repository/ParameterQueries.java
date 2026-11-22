package academy.llavor.academia.parameter.repository;
import academy.llavor.academia.parameter.dto.*;
import jakarta.persistence.*;
public final class ParameterQueries {
 private ParameterQueries(){}
 public static final String VIEW="SELECT new academy.llavor.academia.parameter.dto.ParameterView(p.id,p.key,p.name,p.description,p.group,p.type,p.active,p.editable,p.critical,p.sensitive,p.visible,p.sortOrder,CASE WHEN p.sensitive = FALSE THEN p.value ELSE NULL END,p.modifiedBy,p.updatedAt,p.version) ";
 public static final String SNAPSHOT="SELECT new academy.llavor.academia.parameter.dto.ParameterSnapshot(p.key,p.type,p.active,p.sensitive,CASE WHEN p.sensitive = FALSE THEN p.value ELSE NULL END) ";
 public static String filters(ParameterFilter f){return (f.text()==null?"":" AND (LOWER(p.key) LIKE :text ESCAPE '!' OR LOWER(p.name) LIKE :text ESCAPE '!')")
  +(f.group()==null?"":" AND p.group = :group")+(f.type()==null?"":" AND p.type = :type")+(f.active()==null?"":" AND p.active = :active");}
 public static void bind(Query query,ParameterFilter f){
  if(f.text()!=null)query.setParameter("text","%"+f.text().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
  if(f.group()!=null)query.setParameter("group",f.group());if(f.type()!=null)query.setParameter("type",f.type());if(f.active()!=null)query.setParameter("active",f.active());
 }
 public static java.util.List<String> keys(java.util.List<String> keys){
  if(keys==null||keys.size()>100)throw new IllegalArgumentException("Lote no valido.");
  return keys.stream().map(academy.llavor.academia.parameter.validation.ParameterValidation::key).distinct().toList();
 }
}
