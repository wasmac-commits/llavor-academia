package academy.llavor.academia.classroom.entity;

import academy.llavor.academia.persistence.CanonicalUuidConverter;
import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/** Minimal confirmed tenant identity. No users, courses, contents or administrative activation. */
@Entity @Cacheable(false)
@Table(name = "classrooms", uniqueConstraints = {
    @UniqueConstraint(name = "uk_local_classroom_identity", columnNames = {"portal_instance_id", "portal_classroom_id"}),
    @UniqueConstraint(name = "uk_local_classroom_code", columnNames = {"portal_instance_id", "code"}),
    @UniqueConstraint(name = "uk_local_classroom_context", columnNames = {"id", "portal_instance_id", "portal_classroom_id", "technical_academy_id"})
}, indexes = @Index(name = "ix_local_classroom_academy", columnList = "technical_academy_id, id"))
public class LocalClassroom implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "portal_instance_id", nullable = false, length = 36, updatable = false)
    private UUID portalInstanceId;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "portal_classroom_id", nullable = false, length = 36, updatable = false)
    private UUID portalClassroomId;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "technical_academy_id", nullable = false, length = 36, updatable = false)
    private UUID technicalAcademyId;
    @Column(nullable = false, length = 80, updatable = false)
    private String code;
    @Column(nullable = false, length = 190, updatable = false)
    private String name;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "prepared_at", nullable = false, updatable = false)
    private LocalDateTime preparedAt;
    @Version @Column(nullable = false)
    private long version;
    @Transient private List<Object> persistedIdentity;

    protected LocalClassroom() {}
    public LocalClassroom(UUID portalInstanceId, UUID portalClassroomId, UUID technicalAcademyId, String code, String name) {
        this.portalInstanceId = ProvisioningValidation.uuid(portalInstanceId);
        this.portalClassroomId = ProvisioningValidation.uuid(portalClassroomId);
        this.technicalAcademyId = ProvisioningValidation.uuid(technicalAcademyId);
        this.code = ProvisioningValidation.code(code);
        this.name = ProvisioningValidation.name(name);
    }
    private List<Object> identity() { return Arrays.asList(portalInstanceId, portalClassroomId, technicalAcademyId, code, name); }
    @PostLoad @PostPersist private void rememberIdentity() { persistedIdentity = identity(); }
    private void validate() {
        ProvisioningValidation.uuid(portalInstanceId);
        ProvisioningValidation.uuid(portalClassroomId);
        ProvisioningValidation.uuid(technicalAcademyId);
        if (!Objects.equals(code, ProvisioningValidation.code(code)) || !Objects.equals(name, ProvisioningValidation.name(name)))
            throw new IllegalStateException("La identidad del aula debe estar normalizada.");
        if (version < 0) throw new IllegalStateException("La version no puede ser negativa.");
        if (persistedIdentity != null && !persistedIdentity.equals(identity()))
            throw new IllegalStateException("La identidad y el nombre inicial del aula no se modifican mediante aprovisionamiento.");
    }
    @PrePersist private void beforeInsert() {
        validate();
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC()).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        createdAt = now; updatedAt = now; preparedAt = now;
    }
    @PreUpdate private void beforeUpdate() { validate(); updatedAt = LocalDateTime.now(Clock.systemUTC()).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    public Long getId() { return id; }
    public UUID getPortalInstanceId() { return portalInstanceId; }
    public UUID getPortalClassroomId() { return portalClassroomId; }
    public UUID getTechnicalAcademyId() { return technicalAcademyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public long getVersion() { return version; }
}
