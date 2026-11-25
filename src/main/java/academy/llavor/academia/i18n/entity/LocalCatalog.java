package academy.llavor.academia.i18n.entity;
import academy.llavor.academia.i18n.core.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
/** Only the last accepted publication; bad/stale imports never modify this row. */
@Entity @Cacheable(false) @Table(name="i18n_local_catalog")
public class LocalCatalog {
    @Id private Long id=1L;
    @Column(name="catalog_version",nullable=false) private long version;
    @Column(name="source_portal_id",length=36) private String source;
    @Lob @Column(columnDefinition="LONGTEXT") private String payload;
    @Column(length=64) private String digest;
    @Column(name="installed_at") private LocalDateTime installedAt;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
    public LocalCatalog(){}
    public boolean install(CatalogSnapshot snapshot,String json,String sha,UUID expectedPortal){
        if(snapshot.version()<1||!snapshot.portalInstanceId().equals(expectedPortal)||!snapshot.catalogCode().equals("ui"))throw new IllegalArgumentException("Foreign catalog");
        if(version>0&&!expectedPortal.toString().equals(source))throw new IllegalStateException("Catalog belongs to another Portal");
        if(!CatalogCodec.decode(json,sha).equals(snapshot))throw new IllegalArgumentException("Payload mismatch");
        if(snapshot.publishedAt().isAfter(Instant.now().plusSeconds(300)))throw new IllegalArgumentException("Future publication");
        if(version>0&&snapshot.publishedAt().isBefore(snapshot(expectedPortal).publishedAt()))throw new IllegalArgumentException("Backdated publication");
        if(snapshot.version()<version)throw new IllegalArgumentException("Stale catalog");
        if(snapshot.version()==version){if(sha.equals(digest))return false;throw new IllegalArgumentException("Conflicting publication");}
        source=expectedPortal.toString();version=snapshot.version();payload=json;digest=sha;
        installedAt=LocalDateTime.ofInstant(Instant.now(),ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);return true;
    }
    public CatalogSnapshot snapshot(UUID expectedPortal){
        if(version==0)return BootstrapCatalog.spanish(expectedPortal);
        var snapshot=CatalogCodec.decode(payload,digest);
        if(snapshot.version()!=version||!expectedPortal.toString().equals(source)||!snapshot.portalInstanceId().equals(expectedPortal)||!snapshot.catalogCode().equals("ui"))throw new IllegalStateException("Local catalog mismatch");return snapshot;
    }
    public long getVersion(){return version;}
}
