package academy.llavor.academia.parameter.entity;
import jakarta.persistence.*;
@Entity @Cacheable(false)
@Table(name="academy_parameters",uniqueConstraints=@UniqueConstraint(name="uk_academy_local_parameters_key",columnNames="param_key"),
       indexes=@Index(name="ix_academy_local_parameters_catalogue",columnList="param_group, active, sort_order"))
public class AcademyParameter extends AbstractParameter {
    protected AcademyParameter() {}
    public AcademyParameter(ParameterDefinition definition,String value){super(definition,value);validateScope();}
    @Override protected void validateScope(){
        if(getType()==ParameterType.ENCRYPTED_SECRET||getKey().startsWith("llavor.portal."))throw new IllegalArgumentException("Ambito no valido.");
        if(getGroup()==ParameterGroup.IDENTITY||getType()==ParameterType.UUID)throw new IllegalArgumentException("La identidad pertenece a academy_instance.");
    }
}
