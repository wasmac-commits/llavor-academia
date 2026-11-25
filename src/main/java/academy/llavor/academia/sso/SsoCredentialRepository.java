package academy.llavor.academia.sso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.util.Optional;
/** Read-only boundary for the SSO provider; never a generic parameter endpoint. */
@ApplicationScoped @Transactional(Transactional.TxType.MANDATORY)
public class SsoCredentialRepository {
    @PersistenceContext(unitName="LlavorAcademiaPU") private EntityManager em;
    public Optional<SsoStoredCredential> find(){return Optional.ofNullable(em.find(SsoStoredCredential.class,1L));}
}
