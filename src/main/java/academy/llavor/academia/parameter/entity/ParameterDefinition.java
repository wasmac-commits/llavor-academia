package academy.llavor.academia.parameter.entity;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import java.io.Serializable;

/** Stable catalogue metadata. Sensitive/critical keys cannot be edited by the ordinary editor. */
public record ParameterDefinition(String key,String name,String description,ParameterGroup group,ParameterType type,
                                 boolean critical,boolean sensitive,boolean editable,boolean visible,int sortOrder) implements Serializable {
    public ParameterDefinition {
        key=ParameterValidation.key(key);
        name=ParameterValidation.text(name,190,true);
        description=ParameterValidation.text(description,500,false);
        if(group==null || type==null || sortOrder<0)throw new IllegalArgumentException("Catalogo de parametros no valido.");
        ParameterValidation.catalogueType(key,type);
        if(ParameterValidation.secretType(type)!=sensitive
            || ((sensitive || group==ParameterGroup.IDENTITY || group==ParameterGroup.SECURITY
                || ParameterValidation.protectedKey(key)) && !critical)
            || ((critical||sensitive)&&editable))throw new IllegalArgumentException("Politica de parametro no valida.");
    }
}
