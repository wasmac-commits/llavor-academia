package academy.llavor.academia.provisioning.service;

import academy.llavor.academia.provisioning.config.*;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.security.ProvisioningPrincipal;
import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;

/** Orchestrates independent transactions; it never catches a uniqueness race inside a failed JTA transaction. */
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class ClassroomProvisioningService {
    private ProvisioningTransactions transactions;
    private AcademiaProvisioningConfiguration configuration;
    protected ClassroomProvisioningService() {}
    @Inject public ClassroomProvisioningService(ProvisioningTransactions transactions, AcademiaProvisioningConfiguration configuration) {
        this.transactions = transactions; this.configuration = configuration;
    }

    public ProvisioningReply prepare(ProvisioningPrincipal principal, String academy, String classroom,
                                     String operation, ClassroomProvisioningRequest request) {
        requirePrincipal(principal);
        ProvisioningCommand command = ProvisioningCommand.from(principal, academy, classroom, operation, request);
        try { return transactions.prepare(command); }
        catch (ProvisioningFailure failure) { throw failure; }
        catch (RuntimeException failure) {
            if (!uniqueConflict(failure)) throw new ProvisioningFailure(TEMPORARILY_UNAVAILABLE);
            // REQUIRES_NEW proxy call starts only after prepare's rollback/failed commit returned.
            return sanitized(() -> transactions.reconcile(command));
        }
    }

    /** No classroom/receipt repository or write transaction is involved in this identity probe. */
    public AcademyIdentityResult getIdentity(ProvisioningPrincipal principal, String academy) {
        requirePrincipal(principal);
        requireAcademy(principal, uuid(academy));
        return new AcademyIdentityResult(principal);
    }
    public ClassroomProvisioningResult getOperation(ProvisioningPrincipal principal, String academy,
                                                    String classroom, String operation) {
        requirePrincipal(principal);
        UUID academyId = uuid(academy), classroomId = uuid(classroom), operationId = uuid(operation);
        requireAcademy(principal, academyId);
        return sanitized(() -> transactions.getOperation(principal.portalId(), academyId, classroomId, operationId));
    }
    public ClassroomIdentityResult getClassroom(ProvisioningPrincipal principal, String academy, String classroom) {
        requirePrincipal(principal);
        UUID academyId = uuid(academy), classroomId = uuid(classroom);
        requireAcademy(principal, academyId);
        return sanitized(() -> transactions.getClassroom(principal.portalId(), academyId, classroomId));
    }

    private void requirePrincipal(ProvisioningPrincipal principal) {
        if (principal == null) throw new ProvisioningFailure(UNAUTHORIZED);
        AcademiaIdentity identity = configuration.loadEnabled();
        if (!identity.academyId().equals(principal.academyId()) || !identity.academyCode().equals(principal.academyCode()))
            throw new ProvisioningFailure(ACADEMY_IDENTITY_MISMATCH);
        if (!identity.portalId().equals(principal.portalId())) throw new ProvisioningFailure(PORTAL_IDENTITY_MISMATCH);
        if (!identity.verifierParameters().containsKey(principal.getName())) throw new ProvisioningFailure(UNAUTHORIZED);
    }
    private static void requireAcademy(ProvisioningPrincipal principal, UUID academy) {
        if (!principal.academyId().equals(academy)) throw new ProvisioningFailure(ACADEMY_IDENTITY_MISMATCH);
    }
    private static UUID uuid(String value) {
        try { return ProvisioningValidation.parseUuid(value); }
        catch (IllegalArgumentException ignored) { throw new ProvisioningFailure(INVALID_REQUEST); }
    }
    private static <T> T sanitized(Supplier<T> action) {
        try { return action.get(); }
        catch (ProvisioningFailure failure) { throw failure; }
        catch (RuntimeException ignored) { throw new ProvisioningFailure(TEMPORARILY_UNAVAILABLE); }
    }
    private static boolean uniqueConflict(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause())
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) return true;
        return false;
    }
}
