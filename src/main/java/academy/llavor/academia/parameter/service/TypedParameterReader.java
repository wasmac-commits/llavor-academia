package academy.llavor.academia.parameter.service;
import academy.llavor.academia.parameter.dto.*;
import academy.llavor.academia.parameter.entity.*;
import academy.llavor.academia.parameter.validation.ParameterValidation;
import java.net.URI;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static academy.llavor.academia.parameter.dto.ParameterReadResult.Status.*;
public final class TypedParameterReader {
 private TypedParameterReader(){}
 public static ParameterReadResult<Object> decode(ParameterSnapshot p,ParameterType expected){
  if(p==null)return ParameterReadResult.state(MISSING);
  if(!p.active())return ParameterReadResult.state(DISABLED);
  if(p.sensitive()||p.type()==ParameterType.ENCRYPTED_SECRET||p.type()==ParameterType.SHA256_VERIFIER)return ParameterReadResult.state(RESTRICTED);
  if(expected==null||p.type()!=expected||p.value()==null)return ParameterReadResult.state(INVALID);
  try{
   String value=ParameterValidation.value(expected,p.value());
   Object typed=switch(expected){
    case TEXT,EMAIL -> value;case BOOLEAN -> Boolean.valueOf(value);case INTEGER -> Long.valueOf(value);
    case DECIMAL -> new BigDecimal(value);case URL -> URI.create(value);case UUID -> UUID.fromString(value);
    case DATE -> LocalDate.parse(value);case DATETIME -> OffsetDateTime.parse(value);
    default -> throw new IllegalArgumentException();};
   return new ParameterReadResult<>(VALID,typed);
  }catch(RuntimeException ignored){return ParameterReadResult.state(INVALID);}
 }
 public static <T> ParameterReadResult<T> cast(ParameterReadResult<Object> result,Class<T> type){return new ParameterReadResult<>(result.status(),result.value()==null?null:type.cast(result.value()));}
}
