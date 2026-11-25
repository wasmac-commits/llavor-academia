package academy.llavor.academia.sso;
import java.time.Instant;
import java.util.UUID;
/** Human authorization only. No technical keys, raw tickets, hashes, global roles or passwords. */
public record SsoAuthorization(UUID accessId,UUID portalId,long userId,UUID academyId,UUID classroomId,
        Instant authorizedAt,Instant accessExpiresAt,String displayName,String interfaceLanguage,long interfaceLanguageVersion) implements java.io.Serializable {
    public SsoAuthorization(UUID accessId,UUID portalId,long userId,UUID academyId,UUID classroomId,Instant authorizedAt,Instant accessExpiresAt,String displayName){this(accessId,portalId,userId,academyId,classroomId,authorizedAt,accessExpiresAt,displayName,"es",-1);}
    public String toString(){return "SsoAuthorization[human metadata]";}
}
