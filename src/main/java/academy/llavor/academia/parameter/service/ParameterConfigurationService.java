package academy.llavor.academia.parameter.service;
import academy.llavor.academia.parameter.dto.*;
import academy.llavor.academia.parameter.entity.*;
import academy.llavor.academia.parameter.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
/** Short read transactions; no defaults, credential cache, HTTP or browser identity. */
@ApplicationScoped @Transactional(Transactional.TxType.REQUIRES_NEW)
public class ParameterConfigurationService {
 private AcademyParameterRepository parameters;
 protected ParameterConfigurationService(){}
 @Inject public ParameterConfigurationService(AcademyParameterRepository parameters){this.parameters=parameters;}
 public ParameterReadResult<Object> read(String key,ParameterType expected){try{return TypedParameterReader.decode(parameters.readBatch(List.of(key)).get(key),expected);}catch(ParameterAccessException failure){throw failure;}catch(RuntimeException ignored){throw new ParameterAccessException(ParameterAccessException.Reason.STORAGE_FAILURE);}}
 public Map<String,ParameterReadResult<Object>> readBatch(List<String> keys){try{
   var valid=ParameterQueries.keys(keys);var values=parameters.readBatch(valid);Map<String,ParameterReadResult<Object>> result=new LinkedHashMap<>();
   for(String key:valid){var p=values.get(key);result.put(key,TypedParameterReader.decode(p,p==null?null:p.type()));}return Map.copyOf(result);
  }catch(RuntimeException ignored){throw new ParameterAccessException(ParameterAccessException.Reason.STORAGE_FAILURE);}}
 public ParameterReadResult<String> text(String key){return TypedParameterReader.cast(read(key,ParameterType.TEXT),String.class);}
 public ParameterReadResult<Boolean> bool(String key){return TypedParameterReader.cast(read(key,ParameterType.BOOLEAN),Boolean.class);}
 public ParameterReadResult<Long> integer(String key){return TypedParameterReader.cast(read(key,ParameterType.INTEGER),Long.class);}
 public ParameterReadResult<java.math.BigDecimal> decimal(String key){return TypedParameterReader.cast(read(key,ParameterType.DECIMAL),java.math.BigDecimal.class);}
 public ParameterReadResult<java.net.URI> url(String key){return TypedParameterReader.cast(read(key,ParameterType.URL),java.net.URI.class);}
 public ParameterReadResult<java.util.UUID> uuid(String key){return TypedParameterReader.cast(read(key,ParameterType.UUID),java.util.UUID.class);}
 public ParameterReadResult<java.time.LocalDate> date(String key){return TypedParameterReader.cast(read(key,ParameterType.DATE),java.time.LocalDate.class);}
 public ParameterReadResult<java.time.OffsetDateTime> dateTime(String key){return TypedParameterReader.cast(read(key,ParameterType.DATETIME),java.time.OffsetDateTime.class);}
 public ParameterReadResult<String> email(String key){return TypedParameterReader.cast(read(key,ParameterType.EMAIL),String.class);}
}
