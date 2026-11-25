package academy.llavor.academia.sso.browser;
import academy.llavor.academia.sso.*;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class BrowserDestinationService {
    private SsoConfiguration config;private AcademyInstanceRepository identity;private LocalClassroomRepository rooms;
    protected BrowserDestinationService(){}
    @Inject public BrowserDestinationService(SsoConfiguration config,AcademyInstanceRepository identity,LocalClassroomRepository rooms){
        this.config=config;this.identity=identity;this.rooms=rooms;
    }
    public BrowserDestination load(UUID classroom){
        try{
            var settings=config.loadBrowserEnabled(classroom);var t=settings.technical();
            var local=identity.find().orElseThrow(()->new BrowserFailure("IDENTITY_MISMATCH"));
            var room=rooms.findByIdentity(t.portalId(),classroom).orElseThrow(()->new BrowserFailure("IDENTITY_MISMATCH"));
            if(!local.getInstanceId().equals(t.academyId())||!local.getPortalInstanceId().equals(t.portalId())
                    ||!room.getTechnicalAcademyId().equals(t.academyId())||!room.getPortalClassroomId().equals(classroom))throw new BrowserFailure("IDENTITY_MISMATCH");
            return new BrowserDestination(settings,local.getAcademyCode(),room.getCode(),room.getName());
        }catch(BrowserFailure|SsoClientFailure e){throw e;}catch(RuntimeException e){throw new BrowserFailure("CONFIGURATION_UNAVAILABLE");}
    }
}
