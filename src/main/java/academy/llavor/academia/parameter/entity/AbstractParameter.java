package academy.llavor.academia.parameter.entity;

import academy.llavor.academia.parameter.validation.ParameterValidation;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** Never bind entities to administrative views. Only getAdministrativeValue exposes ordinary values. */
@MappedSuperclass
public abstract class AbstractParameter implements Serializable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(nullable=false) private Long id;
    @Column(name="param_key",nullable=false,length=120,updatable=false) private String key;
    @Column(nullable=false,length=190,updatable=false) private String name;
    @Column(length=500,updatable=false) private String description;
    @Enumerated(EnumType.STRING) @Column(name="param_group",nullable=false,length=30,updatable=false) private ParameterGroup group;
    @Enumerated(EnumType.STRING) @Column(name="value_type",nullable=false,length=30,updatable=false) private ParameterType type;
    @Column(name="param_value",length=4000) private String value;
    @Column(nullable=false) private boolean active=true;
    @Column(nullable=false,updatable=false) private boolean editable;
    @Column(nullable=false,updatable=false) private boolean critical;
    @Column(name="is_sensitive",nullable=false,updatable=false) private boolean sensitive;
    @Column(nullable=false,updatable=false) private boolean visible;
    @Column(name="sort_order",nullable=false,updatable=false) private int sortOrder;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="modified_by",length=190) private String modifiedBy;
    @Version @Column(nullable=false) private long version;
    @Transient private ParameterDefinition persistedDefinition;

    protected AbstractParameter() {}
    protected AbstractParameter(ParameterDefinition definition,String value) {
        if(definition==null)throw new IllegalArgumentException("Se requiere definicion.");
        key=definition.key();name=definition.name();description=definition.description();group=definition.group();type=definition.type();
        critical=definition.critical();sensitive=definition.sensitive();editable=definition.editable();visible=definition.visible();sortOrder=definition.sortOrder();
        this.value=ParameterValidation.value(type,value);
    }
    private ParameterDefinition definition() { return new ParameterDefinition(key,name,description,group,type,critical,sensitive,editable,visible,sortOrder); }
    /** Domain guard complements, but never replaces, service authorization in the next micropaso. */
    public void updateOrdinaryValue(String newValue,String actor) {
        requireOrdinary();
        String validated=ParameterValidation.value(type,newValue);
        String validActor=ParameterValidation.text(actor,190,true);
        value=validated;modifiedBy=validActor;
    }
    public void setOrdinaryActive(boolean desired,String actor) {
        requireOrdinary();String validActor=ParameterValidation.text(actor,190,true);active=desired;modifiedBy=validActor;
    }
    private void requireOrdinary() {
        if(critical||sensitive||!editable)throw new IllegalStateException("Este parametro requiere un procedimiento administrativo especifico.");
    }
    protected abstract void validateScope();
    @PostLoad @PostPersist private void remember() { persistedDefinition=definition(); }
    private void validate() {
        ParameterDefinition current=definition();
        if(persistedDefinition!=null&&!persistedDefinition.equals(current))throw new IllegalStateException("El catalogo del parametro es inmutable.");
        ParameterValidation.value(type,value);
        if(version<0)throw new IllegalStateException("Version no valida.");
        if(modifiedBy!=null&&!modifiedBy.equals(ParameterValidation.text(modifiedBy,190,true)))throw new IllegalStateException("Actor no valido.");
        validateScope();
    }
    @PrePersist private void insert() { validate();createdAt=LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MICROS);updatedAt=createdAt; }
    @PreUpdate private void update() { validate();updatedAt=LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MICROS); }
    public Long getId(){return id;}
    public String getKey(){return key;}
    public String getName(){return name;}
    public String getDescription(){return description;}
    public ParameterGroup getGroup(){return group;}
    public ParameterType getType(){return type;}
    public boolean isActive(){return active;}
    public boolean isEditable(){return editable;}
    public boolean isCritical(){return critical;}
    public boolean isSensitive(){return sensitive;}
    public boolean isVisible(){return visible;}
    public int getSortOrder(){return sortOrder;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
    public String getModifiedBy(){return modifiedBy;}
    public long getVersion(){return version;}
    public String getAdministrativeValue(){return sensitive?null:value;}
    // No getValue(), getSecret(), public raw-value accessor or value-bearing toString().
}
