package academy.llavor.academia.parameter.security;
import academy.llavor.academia.parameter.entity.*;
import academy.llavor.academia.parameter.service.ParameterAccessException;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import static academy.llavor.academia.parameter.service.ParameterAccessException.Reason.*;
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class JpaAcademyVerifierParameterReader implements AcademyVerifierParameterReader {
 @PersistenceContext(unitName="LlavorAcademiaPU") private EntityManager em;
 @Override public SensitiveParameterValue read(String keyId){try{
   if(keyId==null||!keyId.matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException();
  String key="llavor.academia.provisioning.keys."+keyId+".verifier";
  var rows=em.createQuery("SELECT p.type,p.active,p.sensitive,p.critical,p.editable,p.value FROM AcademyParameter p WHERE p.key = :key",Object[].class)
   .setParameter("key",key).setMaxResults(1).getResultList();
  if(rows.isEmpty())throw new ParameterAccessException(NOT_FOUND);
  var row=rows.getFirst();
  if(!Boolean.TRUE.equals(row[1]))throw new ParameterAccessException(FORBIDDEN);
  if(row[0]!=ParameterType.SHA256_VERIFIER||!Boolean.TRUE.equals(row[2])||!Boolean.TRUE.equals(row[3])||!Boolean.FALSE.equals(row[4])||row[5]==null)throw new ParameterAccessException(INVALID_DATA);
  return new SensitiveParameterValue(ParameterValidation.value(ParameterType.SHA256_VERIFIER,(String)row[5]));
 }catch(ParameterAccessException safe){throw safe;}catch(IllegalArgumentException ignored){throw new ParameterAccessException(INVALID_DATA);}catch(RuntimeException ignored){throw new ParameterAccessException(STORAGE_FAILURE);}}
}
