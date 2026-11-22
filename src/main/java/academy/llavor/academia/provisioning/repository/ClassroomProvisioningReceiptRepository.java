package academy.llavor.academia.provisioning.repository;

import academy.llavor.academia.provisioning.entity.ClassroomProvisioningReceipt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.util.*;

@ApplicationScoped
public class ClassroomProvisioningReceiptRepository {
    @PersistenceContext(unitName = "LlavorAcademiaPU") private EntityManager em;
    public ClassroomProvisioningReceiptRepository() {}
    ClassroomProvisioningReceiptRepository(EntityManager em) { this.em = em; }
    public Optional<ClassroomProvisioningReceipt> findByOperation(UUID portal, UUID operation) {
        return em.createQuery("SELECT r FROM ClassroomProvisioningReceipt r LEFT JOIN FETCH r.localClassroom WHERE r.portalInstanceId = :portal AND r.operationId = :operation",
            ClassroomProvisioningReceipt.class).setParameter("portal", portal).setParameter("operation", operation)
            .setMaxResults(1).getResultList().stream().findFirst();
    }
    public boolean containsOtherInstance(UUID portal, UUID academy) {
        return em.createQuery("SELECT COUNT(r) FROM ClassroomProvisioningReceipt r WHERE r.portalInstanceId <> :portal OR r.technicalAcademyId <> :academy", Long.class)
            .setParameter("portal", portal).setParameter("academy", academy).getSingleResult() > 0;
    }
    @Transactional(Transactional.TxType.MANDATORY)
    public ClassroomProvisioningReceipt create(ClassroomProvisioningReceipt receipt) {
        if (receipt == null || receipt.getId() != null) throw new IllegalArgumentException("Se requiere un recibo nuevo.");
        em.persist(receipt); em.flush(); return receipt;
    }
}
