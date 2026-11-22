package academy.llavor.academia.parameter.repository;
import academy.llavor.academia.parameter.dto.*;
import academy.llavor.academia.parameter.entity.*;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.util.*;
/** Administrative reads are safe constructor projections. No unbounded findAll or raw-value accessor. */
@ApplicationScoped
public class AcademyParameterRepository {
 @PersistenceContext(unitName="LlavorAcademiaPU") private EntityManager em;
 public List<ParameterView> findPage(ParameterFilter filter,int first,int size){
   ParameterFilter.page(first,size);filter=filter==null?ParameterFilter.ALL:filter;
  var query=em.createQuery(ParameterQueries.VIEW+"FROM AcademyParameter p WHERE 1=1"+ParameterQueries.filters(filter)+" ORDER BY p.group,p.sortOrder,p.key,p.id",ParameterView.class);
   ParameterQueries.bind(query,filter);return query.setFirstResult(first).setMaxResults(size).getResultList();
 }
 public long count(ParameterFilter filter){
   filter=filter==null?ParameterFilter.ALL:filter;
  var query=em.createQuery("SELECT COUNT(p) FROM AcademyParameter p WHERE 1=1"+ParameterQueries.filters(filter),Long.class);
   ParameterQueries.bind(query,filter);return query.getSingleResult();
 }
 public Optional<ParameterView> findById(Long id){
   if(id==null||id<=0)throw new IllegalArgumentException("Parametro no valido.");
  var query=em.createQuery(ParameterQueries.VIEW+"FROM AcademyParameter p WHERE p.id = :id",ParameterView.class).setParameter("id",id);
  return query.setMaxResults(1).getResultList().stream().findFirst();
 }
 public Optional<ParameterView> findByKey(String key){
   var query=em.createQuery(ParameterQueries.VIEW+"FROM AcademyParameter p WHERE p.key = :key",ParameterView.class).setParameter("key",ParameterValidation.key(key));
  return query.setMaxResults(1).getResultList().stream().findFirst();
 }
 public Map<String,ParameterSnapshot> readBatch(List<String> keys){
   var valid=ParameterQueries.keys(keys);if(valid.isEmpty())return Map.of();
  var query=em.createQuery(ParameterQueries.SNAPSHOT+"FROM AcademyParameter p WHERE p.key IN :keys",ParameterSnapshot.class).setParameter("keys",valid);
  Map<String,ParameterSnapshot> result=new HashMap<>();for(var value:query.setMaxResults(100).getResultList())result.put(value.key(),value);return Map.copyOf(result);
 }
 @Transactional(Transactional.TxType.MANDATORY)
 public Optional<AcademyParameter> findForUpdate(Long id){
   if(id==null||id<=0)throw new IllegalArgumentException("Parametro no valido.");
  var query=em.createQuery("SELECT p FROM AcademyParameter p WHERE p.id = :id AND p.sensitive = FALSE AND p.critical = FALSE AND p.editable = TRUE",AcademyParameter.class).setParameter("id",id);
  var result=query.setMaxResults(1).getResultList().stream().findFirst();result.ifPresent(p->em.refresh(p,LockModeType.OPTIMISTIC));return result;
 }
 @Transactional(Transactional.TxType.MANDATORY) public void flush(){em.flush();}
}
