package academy.llavor.academia.parameter.dto;
import academy.llavor.academia.parameter.entity.*;
import java.io.Serializable;
import java.time.LocalDateTime;
/** Safe projection: sensitive values are masked in SQL and defensively discarded here. */
public record ParameterView(Long id,String key,String name,String description,ParameterGroup group,ParameterType type,
 boolean active,boolean editable,boolean critical,boolean sensitive,boolean visible,int sortOrder,String value,
 String modifiedBy,LocalDateTime updatedAt,long version) implements Serializable {
 public ParameterView { if(sensitive||type==ParameterType.ENCRYPTED_SECRET||type==ParameterType.SHA256_VERIFIER)value=null; }
 public static ParameterView from(AbstractParameter p){return new ParameterView(p.getId(),p.getKey(),p.getName(),p.getDescription(),p.getGroup(),p.getType(),p.isActive(),p.isEditable(),p.isCritical(),p.isSensitive(),p.isVisible(),p.getSortOrder(),p.getAdministrativeValue(),p.getModifiedBy(),p.getUpdatedAt(),p.getVersion());}
}
