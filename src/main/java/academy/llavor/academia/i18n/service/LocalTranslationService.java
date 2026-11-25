package academy.llavor.academia.i18n.service;
import academy.llavor.academia.i18n.core.*;
import academy.llavor.academia.i18n.repository.LocalCatalogRepository;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.provisioning.config.ExpectedInstallationIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
/** Local database only. No HTTP, scheduler, provisioning worker or browser endpoint. */
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class LocalTranslationService {
    private LocalCatalogRepository repository;private AcademyInstanceRepository identity;private ExpectedInstallationIdentity pin;
    protected LocalTranslationService(){}
    @Inject public LocalTranslationService(LocalCatalogRepository repository,AcademyInstanceRepository identity,ExpectedInstallationIdentity pin){this.repository=repository;this.identity=identity;this.pin=pin;}
    private UUID portal(){var own=identity.find().orElseThrow(()->new IllegalStateException("Academy identity required"));pin.require(own.getInstanceId());return own.getPortalInstanceId();}
    /** Future authenticated distribution adapter only. Digest is integrity, NOT proof of source authenticity. */
    public boolean install(String payload,String digest){
        var snapshot=CatalogCodec.decode(payload,digest);UUID portal=portal();var row=repository.require(true);
        boolean changed=row.install(snapshot,payload,digest,portal);if(changed)repository.flush();return changed;
    }
    public CatalogSnapshot snapshot(){UUID portal=portal();return repository.installed()?repository.require(false).snapshot(portal):BootstrapCatalog.spanish(portal);}
    public Map<String,String> enabledLanguages(){return snapshot().languages();}
    public CatalogSnapshot.Resolution resolve(String language,String key,Object... args){return snapshot().resolve(language,key,args);}
}
