package academy.llavor.academia.installation.entity;
import academy.llavor.academia.persistence.CanonicalUuidConverter;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
/** Explicit bootstrap only. No generated IDs/UUIDs and no mutator or startup initializer. */
@Entity @Cacheable(false)
@Table(name="academy_instance",uniqueConstraints={@UniqueConstraint(name="uk_academy_instance_uuid",columnNames="instance_id"),
    @UniqueConstraint(name="uk_academy_instance_receipt_identity",columnNames={"id","instance_id","portal_instance_id","academy_code"})})
public class AcademyInstance implements Serializable {
    @Id @Column(nullable=false,updatable=false) private Long id=1L;
    @Convert(converter=CanonicalUuidConverter.class) @Column(name="instance_id",nullable=false,length=36,updatable=false) private UUID instanceId;
    @Column(name="academy_code",nullable=false,length=80,updatable=false) private String academyCode;
    @Convert(converter=CanonicalUuidConverter.class) @Column(name="portal_instance_id",nullable=false,length=36,updatable=false) private UUID portalInstanceId;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    protected AcademyInstance() {}
    public AcademyInstance(UUID academy,String code,UUID portal){
        instanceId=ParameterValidation.uuid(academy);portalInstanceId=ParameterValidation.uuid(portal);
        if(code==null||!code.matches("[A-Z0-9_]{1,80}"))throw new IllegalArgumentException("Codigo canonico obligatorio.");
        academyCode=code;
    }
    @PrePersist private void insert(){
        if(!Long.valueOf(1L).equals(id))throw new IllegalStateException("Solo una identidad por base de datos.");
        ParameterValidation.uuid(instanceId);ParameterValidation.uuid(portalInstanceId);
        if(academyCode==null||!academyCode.matches("[A-Z0-9_]{1,80}"))throw new IllegalStateException("Codigo no valido.");
        createdAt=LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MICROS);
    }
    @PreUpdate private void rejectUpdate(){throw new IllegalStateException("La identidad de instalacion es inmutable.");}
    public void requireExpectedInstanceId(UUID expected){
        if(!instanceId.equals(ParameterValidation.uuid(expected)))throw new IllegalStateException("Identidad de instalacion incorrecta.");
    }
    public Long getId(){return id;}
    public UUID getInstanceId(){return instanceId;}
    public String getAcademyCode(){return academyCode;}
    public UUID getPortalInstanceId(){return portalInstanceId;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
