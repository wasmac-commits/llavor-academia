package academy.llavor.academia.credential.entity;

import academy.llavor.academia.credential.validation.CredentialMetadata;
import academy.llavor.academia.installation.entity.AcademyInstance;
import academy.llavor.academia.persistence.CanonicalUuidConverter;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.*;
import java.util.*;

/** Local attestation, never proof of HTTP authentication. No secret material. */
@Entity @Cacheable(false)
@Table(name="credential_installation_receipts",
    uniqueConstraints=@UniqueConstraint(name="uk_credential_receipt_revision",columnNames={"operation_id","package_revision"}),indexes={
    @Index(name="ix_credential_receipt_identity",columnList="academy_instance_id, academy_instance_uuid, portal_instance_uuid, academy_code"),
    @Index(name="ix_credential_receipt_key",columnList="academy_instance_id, key_id, verified_at")})
public class CredentialInstallationReceipt implements Serializable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Convert(converter=CanonicalUuidConverter.class)
    @Column(name="operation_id",nullable=false,length=36,updatable=false) private UUID operationId;
    @Column(name="package_revision",nullable=false,updatable=false) private int packageRevision;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="academy_instance_id",nullable=false,updatable=false,foreignKey=@ForeignKey(name="fk_credential_receipt_instance")) private AcademyInstance academyInstance;
    @Convert(converter=CanonicalUuidConverter.class)
    @Column(name="portal_instance_uuid",nullable=false,length=36,updatable=false) private UUID portalInstanceId;
    @Convert(converter=CanonicalUuidConverter.class)
    @Column(name="academy_instance_uuid",nullable=false,length=36,updatable=false) private UUID academyInstanceId;
    @Column(name="academy_code",nullable=false,length=80,updatable=false) private String academyCode;
    @Column(name="key_id",nullable=false,length=32,updatable=false) private String keyId;
    @Column(name="package_sha256",nullable=false,length=64,updatable=false) private String packageSha256;
    @Column(name="configuration_fingerprint",nullable=false,length=64,updatable=false) private String configurationFingerprint;
    @Enumerated(EnumType.STRING) @Column(name="installation_result",nullable=false,length=20) private CredentialInstallationResult result=CredentialInstallationResult.INSTALLED;
    @Column(name="installed_at",nullable=false,updatable=false) private LocalDateTime installedAt;
    @Column(name="verified_at",nullable=false) private LocalDateTime verifiedAt;
    @Version @Column(nullable=false) private long version;

    protected CredentialInstallationReceipt() {}
    public CredentialInstallationReceipt(UUID operation,int revision,AcademyInstance identity,String key,String packageHash,
            String configurationHash,Instant installed,Instant verified) {
        operationId=CredentialMetadata.operation(operation);CredentialMetadata.revision(revision);packageRevision=revision;
        academyInstance=Objects.requireNonNull(identity);academyInstanceId=identity.getInstanceId();portalInstanceId=identity.getPortalInstanceId();academyCode=identity.getAcademyCode();
        keyId=CredentialMetadata.keyId(key);packageSha256=CredentialMetadata.hash(packageHash);configurationFingerprint=CredentialMetadata.hash(configurationHash);
        installedAt=CredentialMetadata.utc(installed);verifiedAt=CredentialMetadata.utc(verified);validate();
    }
    /** Re-read persistence before calling; no overwrite of a contradictory package or configuration. */
    public void confirmExisting(String packageHash,String configurationHash,Instant verified) {
        if(!packageSha256.equals(CredentialMetadata.hash(packageHash))||!configurationFingerprint.equals(CredentialMetadata.hash(configurationHash)))throw new IllegalStateException("Instalacion contradictoria.");
        var at=CredentialMetadata.utc(verified);if(at.isBefore(verifiedAt))throw new IllegalArgumentException("Verificacion anterior a la registrada.");
        verifiedAt=at;result=CredentialInstallationResult.ALREADY_INSTALLED;
    }
    @PrePersist @PreUpdate private void validate() {
        CredentialMetadata.operation(operationId);CredentialMetadata.revision(packageRevision);CredentialMetadata.keyId(keyId);
        CredentialMetadata.hash(packageSha256);CredentialMetadata.hash(configurationFingerprint);Objects.requireNonNull(result);
        if(academyInstance==null||!Long.valueOf(1).equals(academyInstance.getId())
            ||!Objects.equals(academyInstanceId,academyInstance.getInstanceId())||!Objects.equals(portalInstanceId,academyInstance.getPortalInstanceId())
            ||!Objects.equals(academyCode,academyInstance.getAcademyCode())||academyInstanceId==null||portalInstanceId==null
            ||academyCode==null||!academyCode.matches("[A-Z0-9_]{1,80}"))throw new IllegalStateException("Identidad permanente contradictoria.");
        if(version<0||installedAt==null||verifiedAt==null||verifiedAt.isBefore(installedAt))throw new IllegalStateException("Metadatos de recibo no validos.");
    }
    public Long getId(){return id;}
    public UUID getOperationId(){return operationId;}
    public int getPackageRevision(){return packageRevision;}
    public AcademyInstance getAcademyInstance(){return academyInstance;}
    public UUID getPortalInstanceId(){return portalInstanceId;}
    public UUID getAcademyInstanceId(){return academyInstanceId;}
    public String getAcademyCode(){return academyCode;}
    public String getKeyId(){return keyId;}
    public String getPackageSha256(){return packageSha256;}
    public String getConfigurationFingerprint(){return configurationFingerprint;}
    public CredentialInstallationResult getResult(){return result;}
    public LocalDateTime getInstalledAt(){return installedAt;}
    public LocalDateTime getVerifiedAt(){return verifiedAt;}
    public long getVersion(){return version;}
}
