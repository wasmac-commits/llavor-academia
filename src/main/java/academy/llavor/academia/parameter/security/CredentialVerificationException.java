package academy.llavor.academia.parameter.security;
public final class CredentialVerificationException extends RuntimeException {
 private final boolean temporary;
 public CredentialVerificationException(){this(false);}
 public CredentialVerificationException(boolean temporary){super("No se ha podido verificar la credencial de comunicacion.");this.temporary=temporary;}
 public boolean isTemporary(){return temporary;}
}
