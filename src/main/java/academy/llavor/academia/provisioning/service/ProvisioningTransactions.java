package academy.llavor.academia.provisioning.service;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.entity.*;
import academy.llavor.academia.provisioning.repository.ClassroomProvisioningReceiptRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;

/** Separate CDI bean: callers receive detached results only AFTER this interceptor commits. */
@ApplicationScoped
public class ProvisioningTransactions {
    private LocalClassroomRepository classrooms;
    private ClassroomProvisioningReceiptRepository receipts;
    protected ProvisioningTransactions() {}
    @Inject public ProvisioningTransactions(LocalClassroomRepository classrooms, ClassroomProvisioningReceiptRepository receipts) {
        this.classrooms = classrooms; this.receipts = receipts;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProvisioningReply prepare(ProvisioningCommand command) { return prepareWithinTransaction(command); }

    /** Invoked through a separate CDI proxy after the failed prepare transaction has rolled back. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProvisioningReply reconcile(ProvisioningCommand command) { return prepareWithinTransaction(command); }

    private void requireInstance(UUID portal, UUID academy) {
        if (classrooms.containsOtherInstance(portal, academy) || receipts.containsOtherInstance(portal, academy))
            throw new ProvisioningFailure(INVALID_CONFIGURATION);
    }

    private ProvisioningReply prepareWithinTransaction(ProvisioningCommand command) {
        requireInstance(command.portalId(), command.academyId());
        var existingReceipt = receipts.findByOperation(command.portalId(), command.operationId());
        if (existingReceipt.isPresent()) {
            var receipt = existingReceipt.get();
            if (!receipt.getTechnicalAcademyId().equals(command.academyId())
                    || !receipt.getPortalClassroomId().equals(command.classroomId())
                    || !receipt.getRequestHash().equals(command.fingerprint()))
                throw new ProvisioningFailure(OPERATION_CONTENT_CONFLICT);
            return reply(receipt, 200);
        }
        var existing = classrooms.findByIdentity(command.portalId(), command.classroomId());
        LocalClassroom classroom;
        boolean created = existing.isEmpty();
        if (existing.isPresent()) {
            classroom = existing.get();
            if (!classroom.getTechnicalAcademyId().equals(command.academyId())
                    || !classroom.getCode().equals(command.classroomCode()))
                throw new ProvisioningFailure(CLASSROOM_IDENTITY_CONFLICT);
            // The initial name is never updated by a replay/new preparation operation.
        } else {
            if (classrooms.findByCode(command.portalId(), command.classroomCode()).isPresent())
                throw new ProvisioningFailure(CLASSROOM_IDENTITY_CONFLICT);
            classroom = classrooms.create(new LocalClassroom(command.portalId(), command.classroomId(),
                command.academyId(), command.classroomCode(), command.classroomName()));
        }
        var receipt = receipts.create(ClassroomProvisioningReceipt.ready(command.operationId(), command.fingerprint(), classroom));
        return reply(receipt, created ? 201 : 200);
    }

    private static ProvisioningReply reply(ClassroomProvisioningReceipt receipt, int status) {
        return new ProvisioningReply(receipt.getResult() == ProvisioningResult.FAILED ? 422 : status,
            new ClassroomProvisioningResult(receipt));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ClassroomProvisioningResult getOperation(UUID portal, UUID academy, UUID classroom, UUID operation) {
        requireInstance(portal, academy);
        var receipt = receipts.findByOperation(portal, operation).orElseThrow(() -> new ProvisioningFailure(NOT_FOUND));
        if (!receipt.getTechnicalAcademyId().equals(academy) || !receipt.getPortalClassroomId().equals(classroom))
            throw new ProvisioningFailure(NOT_FOUND);
        return new ClassroomProvisioningResult(receipt);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ClassroomIdentityResult getClassroom(UUID portal, UUID academy, UUID classroom) {
        requireInstance(portal, academy);
        var value = classrooms.findByIdentity(portal, classroom).orElseThrow(() -> new ProvisioningFailure(NOT_FOUND));
        if (!value.getTechnicalAcademyId().equals(academy)) throw new ProvisioningFailure(NOT_FOUND);
        return new ClassroomIdentityResult(value);
    }
}
