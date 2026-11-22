package academy.llavor.academia.parameter.audit;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.*;
/** No values, hashes, envelopes, request bodies, exceptions or credentials enter this sink. */
@ApplicationScoped
public class ParameterAuditSink {
 private static final Logger LOG=Logger.getLogger(ParameterAuditSink.class.getName());
 public void committed(ParameterAudit.Entry event){LOG.log(Level.INFO,"Parameter audit actor={0} scope={1} academy={2} key={3} operation={4} utc={5}",new Object[]{event.actor(),event.scope(),event.academy(),event.key(),event.operation(),event.at()});}
}
