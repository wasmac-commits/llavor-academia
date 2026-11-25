package academy.llavor.academia.i18n.repository;
import academy.llavor.academia.i18n.entity.LocalCatalog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
@ApplicationScoped @Transactional(Transactional.TxType.MANDATORY)
public class LocalCatalogRepository {
    @PersistenceContext(unitName="LlavorAcademiaPU") private EntityManager em;
    /** Readiness probe permits Spanish recovery before the separately authorized V005. */
    public boolean installed(){return ((Number)em.createNativeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='i18n_local_catalog'").getSingleResult()).intValue()==1;}
    public LocalCatalog require(boolean write){
        var row=write?em.find(LocalCatalog.class,1L,LockModeType.PESSIMISTIC_WRITE):em.find(LocalCatalog.class,1L);
        if(row==null)throw new IllegalStateException("Local i18n migration not installed");
        if(write)em.refresh(row,LockModeType.PESSIMISTIC_WRITE);else em.refresh(row);return row;
    }
    public void flush(){em.flush();}
}
