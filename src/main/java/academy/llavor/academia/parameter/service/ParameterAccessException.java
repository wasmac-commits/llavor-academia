package academy.llavor.academia.parameter.service;
@jakarta.ejb.ApplicationException(rollback=true)
public final class ParameterAccessException extends RuntimeException {
 public enum Reason { FORBIDDEN, NOT_FOUND, INVALID_DATA, NOT_EDITABLE, STALE_VERSION, STORAGE_FAILURE, ALREADY_INITIALIZED }
 private final Reason reason;
 public ParameterAccessException(Reason reason){super(switch(reason){
  case FORBIDDEN -> "No tienes autorizacion para esta operacion.";
  case NOT_FOUND -> "No se ha encontrado el parametro.";
  case INVALID_DATA -> "Los datos no son validos.";
  case NOT_EDITABLE -> "Este parametro requiere un procedimiento administrativo especifico.";
  case STALE_VERSION -> "El parametro ha cambiado. Recarga los datos antes de guardar.";
  case STORAGE_FAILURE -> "No se ha podido completar la operacion.";
  case ALREADY_INITIALIZED -> "La identidad de la instalacion ya esta inicializada.";});this.reason=reason;}
 public Reason getReason(){return reason;}
}
