package academy.llavor.academia.classroom.repository;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.util.*;

@ApplicationScoped
public class LocalClassroomRepository {
    @PersistenceContext(unitName = "LlavorAcademiaPU") private EntityManager em;
    public LocalClassroomRepository() {}
    LocalClassroomRepository(EntityManager em) { this.em = em; }
    public Optional<LocalClassroom> findByIdentity(UUID portal, UUID classroom) {
        return em.createQuery("SELECT c FROM LocalClassroom c WHERE c.portalInstanceId = :portal AND c.portalClassroomId = :classroom",
            LocalClassroom.class).setParameter("portal", portal).setParameter("classroom", classroom)
            .setMaxResults(1).getResultList().stream().findFirst();
    }
    public Optional<LocalClassroom> findByCode(UUID portal, String code) {
        return em.createQuery("SELECT c FROM LocalClassroom c WHERE c.portalInstanceId = :portal AND c.code = :code",
            LocalClassroom.class).setParameter("portal", portal).setParameter("code", code)
            .setMaxResults(1).getResultList().stream().findFirst();
    }
    public boolean containsOtherInstance(UUID portal, UUID academy) {
        return em.createQuery("SELECT COUNT(c) FROM LocalClassroom c WHERE c.portalInstanceId <> :portal OR c.technicalAcademyId <> :academy", Long.class)
            .setParameter("portal", portal).setParameter("academy", academy).getSingleResult() > 0;
    }
    @Transactional(Transactional.TxType.MANDATORY)
    public LocalClassroom create(LocalClassroom classroom) {
        if (classroom == null || classroom.getId() != null) throw new IllegalArgumentException("Se requiere un aula nueva.");
        em.persist(classroom); em.flush(); return classroom;
    }
}
