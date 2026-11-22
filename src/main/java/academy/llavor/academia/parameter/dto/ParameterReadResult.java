package academy.llavor.academia.parameter.dto;
public record ParameterReadResult<T>(Status status,T value) {
 public enum Status { MISSING, DISABLED, INVALID, RESTRICTED, VALID }
 public ParameterReadResult {if(status==null||(status==Status.VALID)!=(value!=null))throw new IllegalArgumentException("Resultado no valido.");}
 public static <T> ParameterReadResult<T> state(Status status){return new ParameterReadResult<>(status,null);}
}
