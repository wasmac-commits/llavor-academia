package academy.llavor.academia.parameter.audit;
import jakarta.enterprise.context.ApplicationScoped;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import jakarta.inject.Inject;
import jakarta.transaction.*;
import jakarta.annotation.Resource;
import java.time.*;
import java.time.temporal.ChronoUnit;
@ApplicationScoped
public class ParameterAudit {
 public enum Operation { UPDATE_ORDINARY, INITIALIZE_IDENTITY }
 public record Entry(String actor,String scope,String academy,String key,Operation operation,LocalDateTime at) {
  public Entry {if(actor==null||!actor.matches("[A-Za-z0-9_.:@-]{1,190}")||!java.util.Set.of("GLOBAL","ACADEMY","INSTANCE").contains(scope)||
   (academy!=null&&!academy.matches("[A-Za-z0-9_-]{1,80}"))||operation==null||at==null)throw new IllegalArgumentException("Auditoria no valida.");
   ParameterValidation.key(key);}
 }
 @Resource private TransactionSynchronizationRegistry registry;
 @Inject private ParameterAuditSink sink;
 public void record(String actor,String scope,String academy,String key,Operation operation){
  Entry entry=new Entry(actor,scope,academy,key,operation,LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MICROS));
  if(registry.getTransactionKey()==null)throw new IllegalStateException("Auditoria requiere transaccion.");
  registry.registerInterposedSynchronization(new Synchronization(){public void beforeCompletion(){}public void afterCompletion(int status){if(status==Status.STATUS_COMMITTED)sink.committed(entry);}});
 }
}
