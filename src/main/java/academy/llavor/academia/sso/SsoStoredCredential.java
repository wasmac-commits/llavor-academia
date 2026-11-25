package academy.llavor.academia.sso;
import academy.llavor.academia.persistence.CanonicalUuidConverter;
import academy.llavor.academia.sso.crypto.SsoCipher;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
/** Dedicated protected store, outside the generic parameter editor. No public mutation or plaintext. */
@Entity @Cacheable(false) @Table(name="sso_credentials")
public class SsoStoredCredential {
    public enum State { DISABLED, ENABLED }
    @Id @Column(name="id",nullable=false,updatable=false) private Long id=1L;
    @Column(name="academy_instance_id",nullable=false,updatable=false) private Long instanceId=1L;
    @Convert(converter=CanonicalUuidConverter.class) @Column(name="academy_instance_uuid",length=36,nullable=false,updatable=false) private UUID academyId;
    @Convert(converter=CanonicalUuidConverter.class) @Column(name="portal_instance_uuid",length=36,nullable=false,updatable=false) private UUID portalId;
    @Column(name="academy_code",length=80,nullable=false,updatable=false) private String academyCode;
    @Column(name="key_id",length=32,nullable=false,updatable=false) private String keyId;
    @Convert(converter=CanonicalUuidConverter.class) @Column(name="operation_id",length=36,nullable=false,updatable=false) private UUID operationId;
    @Column(name="credential_version",nullable=false,updatable=false) private int credentialVersion;
    @Column(name="master_key_id",length=32,nullable=false,updatable=false) private String masterKeyId;
    @Column(name="encrypted_secret",length=103,nullable=false,updatable=false) private String encryptedSecret;
    @Enumerated(EnumType.STRING) @Column(name="credential_state",length=16,nullable=false) private State state;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Version @Column(name="version",nullable=false) private long version;
    protected SsoStoredCredential(){}
    boolean usable(UUID academy,UUID portal,String code){
        return Long.valueOf(1).equals(id)&&Long.valueOf(1).equals(instanceId)&&academy.equals(academyId)&&portal.equals(portalId)
            &&code.equals(academyCode)&&"S01".equals(keyId)&&"M01".equals(masterKeyId)&&credentialVersion==1&&version>=0
            &&createdAt!=null&&updatedAt!=null&&!updatedAt.isBefore(createdAt)&&state==State.ENABLED;
    }
    SsoCipher.Context context(){return new SsoCipher.Context(portalId,academyId,academyCode,keyId,operationId,credentialVersion,createdAt.toInstant(ZoneOffset.UTC));}
    String encryptedSecret(){return encryptedSecret;}
    public String toString(){return "SsoStoredCredential[redacted]";}
}
