package academy.llavor.academia.provisioning;

import academy.llavor.academia.provisioning.config.*;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.security.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class ReceiverFixture {
    public final UUID portal = UUID.randomUUID(), academy = UUID.randomUUID(), classroom = UUID.randomUUID(), operation = UUID.randomUUID();
    public final String alias = "llavor.academia.provisioning.keys.K01.verifier";
    public final String token;
    public final String verifier;
    public final AcademiaIdentity identity;
    public final ProvisioningPrincipal principal;
    public ReceiverFixture() {
        try {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            verifier = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new AssertionError(e); }
        identity = new AcademiaIdentity(academy, "ACADEMIA_001", portal, Map.of("K01", alias), false);
        principal = new ProvisioningAuthenticator((accepted,key,received) -> accepted.contains(key) && token.equals(received)).authenticate(identity, "Bearer " + token, "K01");
    }
    public ClassroomProvisioningRequest request() {
        var request = new ClassroomProvisioningRequest();
        request.setContractVersion(1); request.setPortalInstanceId(portal.toString());
        request.setAcademyCode("ACADEMIA_001"); request.setClassroomCode("AULA_001"); request.setClassroomName("Aula inicial");
        return request;
    }
    public ProvisioningCommand command() {
        return ProvisioningCommand.from(principal, academy.toString(), classroom.toString(), operation.toString(), request());
    }
    public static void field(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public static void lifecycle(Object target, Class<? extends Annotation> annotation) {
        try {
            Method method = Arrays.stream(target.getClass().getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annotation)).findFirst().orElseThrow();
            method.setAccessible(true); method.invoke(target);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
