package academy.llavor.academia.sso;
import academy.llavor.academia.provisioning.config.ExpectedInstallationIdentity;
import academy.llavor.academia.installation.repository.AcademyInstanceRepository;
import academy.llavor.academia.sso.crypto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
import static academy.llavor.academia.sso.SsoClientFailure.Code.*;
/** The only runtime SSO decryption boundary. No aliases, ordinary secret properties or caches. */
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class SsoCredentialProvider {
    private ExpectedInstallationIdentity pin;private AcademyInstanceRepository identity;private SsoCredentialRepository store;
    private SsoCipher.Keys keys;
    protected SsoCredentialProvider(){}
    @Inject public SsoCredentialProvider(ExpectedInstallationIdentity pin,AcademyInstanceRepository identity,SsoCredentialRepository store){
        this(pin,identity,store,null);
    }
    SsoCredentialProvider(ExpectedInstallationIdentity pin,AcademyInstanceRepository identity,SsoCredentialRepository store,SsoCipher.Keys keys){
        this.pin=pin;this.identity=identity;this.store=store;this.keys=keys;
    }
    public SsoCredential load(UUID academy,String keyId){
        if(!"S01".equals(keyId))throw new SsoClientFailure(INVALID_CONFIGURATION);
        char[] token=null;
        try{
            SsoValidation.uuid(academy);var local=identity.find().orElseThrow(()->new SsoClientFailure(IDENTITY_MISMATCH));
            UUID expected;
            try{expected=pin.require(local.getInstanceId());}catch(RuntimeException e){throw new SsoClientFailure(IDENTITY_MISMATCH);}
            if(!academy.equals(local.getInstanceId())||!academy.equals(expected))throw new SsoClientFailure(IDENTITY_MISMATCH);
            var row=store.find().orElseThrow(()->new SsoClientFailure(CREDENTIAL_UNAVAILABLE));
            if(!row.usable(academy,local.getPortalInstanceId(),local.getAcademyCode()))throw new SsoClientFailure(CREDENTIAL_UNAVAILABLE);
            token=new SsoCipher(keys==null?ExternalMasterKey.configured(academy):keys).decrypt(row.context(),row.encryptedSecret());
            return new SsoCredential(new String(token));
        }catch(SsoClientFailure e){throw e;}
        catch(RuntimeException e){throw new SsoClientFailure(CREDENTIAL_UNAVAILABLE);}
        finally{if(token!=null)Arrays.fill(token,'\0');}
    }
}
