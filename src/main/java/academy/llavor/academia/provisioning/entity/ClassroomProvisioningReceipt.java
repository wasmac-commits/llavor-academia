package academy.llavor.academia.provisioning.entity;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.persistence.CanonicalUuidConverter;
import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Immutable terminal result; a successful receipt and its classroom can share one local JTA transaction. */
@Entity @Cacheable(false)
@Table(name = "classroom_provisioning_receipts", uniqueConstraints =
    @UniqueConstraint(name = "uk_receipt_operation", columnNames = {"portal_instance_id", "operation_id"}),
    indexes = {
        @Index(name = "ix_receipt_classroom_identity", columnList = "portal_instance_id, portal_classroom_id"),
        @Index(name = "ix_receipt_local_context", columnList = "local_classroom_id, portal_instance_id, portal_classroom_id, technical_academy_id")
    })
public class ClassroomProvisioningReceipt implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "portal_instance_id", nullable = false, length = 36, updatable = false)
    private UUID portalInstanceId;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "operation_id", nullable = false, length = 36, updatable = false)
    private UUID operationId;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "portal_classroom_id", nullable = false, length = 36, updatable = false)
    private UUID portalClassroomId;
    @Convert(converter = CanonicalUuidConverter.class)
    @Column(name = "technical_academy_id", nullable = false, length = 36, updatable = false)
    private UUID technicalAcademyId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_classroom_id", updatable = false,
        foreignKey = @ForeignKey(name = "fk_receipt_classroom"))
    private LocalClassroom localClassroom;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10, updatable = false)
    private ProvisioningResult result;
    @Enumerated(EnumType.STRING) @Column(name = "error_code", length = 40, updatable = false)
    private ProvisioningErrorCode errorCode;
    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;

    protected ClassroomProvisioningReceipt() {}

    public static ClassroomProvisioningReceipt ready(UUID operationId, String requestHash, LocalClassroom classroom) {
        if (classroom == null) throw new IllegalArgumentException("Un exito requiere un aula local.");
        ClassroomProvisioningReceipt receipt = base(classroom.getPortalInstanceId(), operationId,
            classroom.getPortalClassroomId(), classroom.getTechnicalAcademyId(), requestHash);
        receipt.localClassroom = classroom;
        receipt.result = ProvisioningResult.READY;
        return receipt;
    }

    public static ClassroomProvisioningReceipt failed(UUID portalId, UUID operationId, UUID classroomId,
                                                      UUID academyId, String requestHash, ProvisioningErrorCode error) {
        if (error != ProvisioningErrorCode.PREPARATION_REJECTED)
            throw new IllegalArgumentException("Un fallo definitivo requiere rechazo funcional confirmado.");
        ClassroomProvisioningReceipt receipt = base(portalId, operationId, classroomId, academyId, requestHash);
        receipt.result = ProvisioningResult.FAILED;
        receipt.errorCode = error;
        return receipt;
    }

    private static ClassroomProvisioningReceipt base(UUID portalId, UUID operationId, UUID classroomId,
                                                      UUID academyId, String requestHash) {
        ClassroomProvisioningReceipt receipt = new ClassroomProvisioningReceipt();
        receipt.portalInstanceId = ProvisioningValidation.uuid(portalId);
        receipt.operationId = ProvisioningValidation.uuid(operationId);
        receipt.portalClassroomId = ProvisioningValidation.uuid(classroomId);
        receipt.technicalAcademyId = ProvisioningValidation.uuid(academyId);
        receipt.requestHash = ProvisioningValidation.fingerprint(requestHash);
        return receipt;
    }
    @PrePersist private void beforeInsert() {
        ProvisioningValidation.uuid(portalInstanceId);
        ProvisioningValidation.uuid(operationId);
        ProvisioningValidation.uuid(portalClassroomId);
        ProvisioningValidation.uuid(technicalAcademyId);
        ProvisioningValidation.fingerprint(requestHash);
        if (result == ProvisioningResult.READY) {
            if (localClassroom == null || localClassroom.getId() == null || localClassroom.getId() <= 0
                    || errorCode != null || !Objects.equals(portalInstanceId, localClassroom.getPortalInstanceId())
                    || !Objects.equals(portalClassroomId, localClassroom.getPortalClassroomId())
                    || !Objects.equals(technicalAcademyId, localClassroom.getTechnicalAcademyId()))
                throw new IllegalStateException("El recibo no corresponde al contexto del aula preparada.");
        } else if (result != ProvisioningResult.FAILED || localClassroom != null
                || errorCode != ProvisioningErrorCode.PREPARATION_REJECTED)
            throw new IllegalStateException("Resultado confirmado no valido.");
        completedAt = LocalDateTime.now(Clock.systemUTC()).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    @PreUpdate private void rejectUpdate() { throw new IllegalStateException("Un recibo confirmado es inmutable."); }
    public Long getId() { return id; }
    public UUID getPortalInstanceId() { return portalInstanceId; }
    public UUID getOperationId() { return operationId; }
    public UUID getPortalClassroomId() { return portalClassroomId; }
    public UUID getTechnicalAcademyId() { return technicalAcademyId; }
    public LocalClassroom getLocalClassroom() { return localClassroom; }
    public String getRequestHash() { return requestHash; }
    public ProvisioningResult getResult() { return result; }
    public ProvisioningErrorCode getErrorCode() { return errorCode; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
