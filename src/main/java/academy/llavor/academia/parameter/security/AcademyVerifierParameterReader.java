package academy.llavor.academia.parameter.security;
/** Privileged technical access, not an administrative listing/editing API. */
public interface AcademyVerifierParameterReader { SensitiveParameterValue read(String keyId); }
