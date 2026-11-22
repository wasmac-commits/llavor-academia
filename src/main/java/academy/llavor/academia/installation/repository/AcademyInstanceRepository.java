package academy.llavor.academia.installation.repository;
import academy.llavor.academia.installation.entity.AcademyInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.util.Optional;
@ApplicationScoped
public class AcademyInstanceRepository {
 @PersistenceContext(unitName="LlavorAcademiaPU") private EntityManager em;
 public Optional<AcademyInstance> find(){return Optional.ofNullable(em.find(AcademyInstance.class,1L));}
 public long count(){return em.createQuery("SELECT COUNT(i) FROM AcademyInstance i",Long.class).getSingleResult();}
 @Transactional(Transactional.TxType.MANDATORY) public AcademyInstance create(AcademyInstance identity){
  if(identity==null||!Long.valueOf(1L).equals(identity.getId()))throw new IllegalArgumentException("Identidad no valida.");
  em.persist(identity);em.flush();return identity;
 }
}
