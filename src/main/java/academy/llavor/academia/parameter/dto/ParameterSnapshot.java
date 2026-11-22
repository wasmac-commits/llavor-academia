package academy.llavor.academia.parameter.dto;
import academy.llavor.academia.parameter.entity.ParameterType;
/** General internal reads also exclude sensitive payloads. */
public record ParameterSnapshot(String key,ParameterType type,boolean active,boolean sensitive,String value) {
 public ParameterSnapshot {if(sensitive||type==ParameterType.ENCRYPTED_SECRET||type==ParameterType.SHA256_VERIFIER)value=null;}
}
