package academy.llavor.academia.provisioning;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.provisioning.config.*;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.entity.*;
import academy.llavor.academia.provisioning.repository.ClassroomProvisioningReceiptRepository;
import academy.llavor.academia.provisioning.service.*;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionalException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiverTransactionsTest {
    /** Transactional test double: staged writes become visible only at commit; uniqueness failures discard ALL staged writes. */
    static class Database {
        final LocalClassroomRepository classrooms = mock(LocalClassroomRepository.class);
        final ClassroomProvisioningReceiptRepository receipts = mock(ClassroomProvisioningReceiptRepository.class);
        final Map<UUID, LocalClassroom> committedClassrooms = new HashMap<>();
        final Map<UUID, ClassroomProvisioningReceipt> committedReceipts = new HashMap<>();
        final ThreadLocal<Tx> current = new ThreadLocal<>();
        final AtomicLong ids = new AtomicLong();
        final List<String> events = new CopyOnWriteArrayList<>();
        final ProvisioningTransactions body = new ProvisioningTransactions(classrooms, receipts);
        final ProvisioningTransactions proxy = mock(ProvisioningTransactions.class);
        final AcademiaProvisioningConfiguration configuration = mock(AcademiaProvisioningConfiguration.class);
        final ClassroomProvisioningService service = new ClassroomProvisioningService(proxy, configuration);
        volatile CyclicBarrier firstCommits;
        static class Tx {
            final Map<UUID, LocalClassroom> classrooms = new HashMap<>();
            final Map<UUID, ClassroomProvisioningReceipt> receipts = new HashMap<>();
        }
        Database(ReceiverFixture f) {
            when(configuration.loadEnabled()).thenReturn(f.identity);
            when(classrooms.findByIdentity(any(), any())).thenAnswer(c -> {
                UUID key = c.getArgument(1);
                synchronized(this) { return Optional.ofNullable(current.get().classrooms.getOrDefault(key, committedClassrooms.get(key))); }
            });
            when(classrooms.findByCode(any(), any())).thenAnswer(c -> {
                String code = c.getArgument(1);
                synchronized(this) { return committedClassrooms.values().stream().filter(v -> v.getCode().equals(code)).findFirst(); }
            });
            when(receipts.findByOperation(any(), any())).thenAnswer(c -> {
                UUID key = c.getArgument(1);
                synchronized(this) { return Optional.ofNullable(current.get().receipts.getOrDefault(key, committedReceipts.get(key))); }
            });
            when(classrooms.create(any())).thenAnswer(c -> {
                LocalClassroom value = c.getArgument(0);
                ReceiverFixture.lifecycle(value, PrePersist.class); ReceiverFixture.field(value, "id", ids.incrementAndGet());
                current.get().classrooms.put(value.getPortalClassroomId(), value); return value;
            });
            when(receipts.create(any())).thenAnswer(c -> {
                ClassroomProvisioningReceipt value = c.getArgument(0);
                ReceiverFixture.lifecycle(value, PrePersist.class); ReceiverFixture.field(value, "id", ids.incrementAndGet());
                current.get().receipts.put(value.getOperationId(), value); return value;
            });
            when(proxy.prepare(any())).thenAnswer(c -> tx(false, () -> body.prepare(c.getArgument(0))));
            when(proxy.reconcile(any())).thenAnswer(c -> tx(true, () -> body.reconcile(c.getArgument(0))));
            when(proxy.getOperation(any(), any(), any(), any())).thenAnswer(c -> tx(false,
                () -> body.getOperation(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3))));
            when(proxy.getClassroom(any(), any(), any())).thenAnswer(c -> tx(false,
                () -> body.getClassroom(c.getArgument(0), c.getArgument(1), c.getArgument(2))));
        }
        <T> T tx(boolean recovery, Supplier<T> work) throws Exception {
            assertNull(current.get()); Tx staged = new Tx(); current.set(staged); events.add(recovery ? "recovery-start" : "start");
            try {
                T result = work.get();
                if (!recovery && firstCommits != null) firstCommits.await(5, TimeUnit.SECONDS);
                synchronized(this) {
                    for (LocalClassroom row : staged.classrooms.values())
                        if (committedClassrooms.containsKey(row.getPortalClassroomId())
                                || committedClassrooms.values().stream().anyMatch(v -> v.getCode().equals(row.getCode()))) throw duplicate();
                    for (UUID id : staged.receipts.keySet()) if (committedReceipts.containsKey(id)) throw duplicate();
                    committedClassrooms.putAll(staged.classrooms); committedReceipts.putAll(staged.receipts);
                }
                events.add("commit"); return result;
            } catch (Exception failure) {
                events.add("rollback"); throw failure;
            } finally { current.remove(); }
        }
        static RuntimeException duplicate() {
            return new TransactionalException("TEST", new PersistenceException(new SQLException("TEST", "23000", 1062)));
        }
        ProvisioningReply put(ReceiverFixture f, UUID classroom, UUID operation, ClassroomProvisioningRequest request) {
            return service.prepare(f.principal, f.academy.toString(), classroom.toString(), operation.toString(), request);
        }
        ProvisioningReply put(ReceiverFixture f) { return put(f, f.classroom, f.operation, f.request()); }
    }

    static void expect(ProvisioningFailure.Code code, Runnable action) {
        var failure = assertThrows(ProvisioningFailure.class, action::run);
        assertEquals(code, failure.getCode()); assertNull(failure.getCause());
    }

    @Test void firstPreparationAndLostResponseReplayReturnSameConfirmedDatesWithoutDuplicating() {
        var f = new ReceiverFixture(); var db = new Database(f);
        var created = db.put(f); var repeated = db.put(f);
        assertEquals(201, created.httpStatus()); assertEquals(200, repeated.httpStatus());
        assertEquals("READY", created.result().getResult());
        assertEquals(created.result().getCompletedAt(), repeated.result().getCompletedAt());
        assertEquals(created.result().getPreparedAt(), repeated.result().getPreparedAt());
        assertEquals(1, db.committedClassrooms.size()); assertEquals(1, db.committedReceipts.size());
        assertTrue(created.result().getPreparedAt().endsWith("Z"));
    }

    @Test void changedOperationContentIsConflictAndNewCompatibleOperationDoesNotRenamePreparedClassroom() {
        var f = new ReceiverFixture(); var db = new Database(f); db.put(f);
        var changed = f.request(); changed.setClassroomName("Nombre posterior");
        expect(OPERATION_CONTENT_CONFLICT, () -> db.put(f, f.classroom, f.operation, changed));
        assertEquals(200, db.put(f, f.classroom, UUID.randomUUID(), changed).httpStatus());
        assertEquals("Aula inicial", db.committedClassrooms.get(f.classroom).getName());
        assertEquals(1, db.committedClassrooms.size()); assertEquals(2, db.committedReceipts.size());
    }

    @Test void existingCodeWithDifferentIdentityIsConflictAndQueriesAreScopedAndAuthenticated() {
        var f = new ReceiverFixture(); var db = new Database(f); db.put(f);
        expect(CLASSROOM_IDENTITY_CONFLICT, () -> db.put(f, UUID.randomUUID(), UUID.randomUUID(), f.request()));
        var result = db.service.getOperation(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString());
        assertEquals(f.operation.toString(), result.getOperationId());
        assertEquals("Aula inicial", db.service.getClassroom(f.principal, f.academy.toString(), f.classroom.toString()).getClassroomName());
        expect(NOT_FOUND, () -> db.service.getOperation(f.principal, f.academy.toString(), UUID.randomUUID().toString(), f.operation.toString()));
        expect(ACADEMY_IDENTITY_MISMATCH, () -> db.service.getClassroom(f.principal, UUID.randomUUID().toString(), f.classroom.toString()));
        expect(UNAUTHORIZED, () -> db.service.getClassroom(null, f.academy.toString(), f.classroom.toString()));
    }

    @Test void simultaneousSameOperationCreatesOneClassroomAndReceiptWithRecoveryInFreshTransaction() throws Exception {
        var f = new ReceiverFixture(); var db = new Database(f); db.firstCommits = new CyclicBarrier(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> db.put(f)); var second = threads.submit(() -> db.put(f));
            Set<Integer> statuses = Set.of(first.get(10, TimeUnit.SECONDS).httpStatus(), second.get(10, TimeUnit.SECONDS).httpStatus());
            assertEquals(Set.of(201, 200), statuses);
        }
        assertEquals(1, db.committedClassrooms.size()); assertEquals(1, db.committedReceipts.size());
        assertEquals(1, db.events.stream().filter("rollback"::equals).count());
        assertEquals(1, db.events.stream().filter("recovery-start"::equals).count());
    }

    @Test void conflictingConcurrentOperationIdsRollbackTheLosingClassroomBeforeReconciliation() throws Exception {
        var f = new ReceiverFixture(); var db = new Database(f); db.firstCommits = new CyclicBarrier(2);
        var other = f.request(); other.setClassroomCode("AULA_002");
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> {
                try { return db.put(f).httpStatus(); } catch (ProvisioningFailure e) { return e.getCode().status(); }
            });
            var second = threads.submit(() -> {
                try { return db.put(f, UUID.randomUUID(), f.operation, other).httpStatus(); }
                catch (ProvisioningFailure e) { return e.getCode().status(); }
            });
            assertEquals(Set.of(201, 409), Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, db.committedClassrooms.size()); assertEquals(1, db.committedReceipts.size());
        assertEquals(1, db.events.stream().filter("recovery-start"::equals).count());
    }

    @Test void disabledWrongInstanceOrChangedCredentialNeverReachPersistence() {
        var f = new ReceiverFixture(); var db = new Database(f);
        when(db.configuration.loadEnabled()).thenThrow(new ProvisioningFailure(PROVISIONING_DISABLED));
        expect(PROVISIONING_DISABLED, () -> db.put(f));
        verifyNoInteractions(db.proxy);
        doReturn(new AcademiaIdentity(UUID.randomUUID(), "ACADEMIA_001", f.portal, Map.of("K01", f.alias), false)).when(db.configuration).loadEnabled();
        expect(ACADEMY_IDENTITY_MISMATCH, () -> db.put(f));
        verifyNoInteractions(db.proxy);
    }

    @Test void transactionBoundariesAndSanitizationCoverCommitFailuresWithoutManufacturingReady() throws Exception {
        assertEquals(Transactional.TxType.NOT_SUPPORTED, ClassroomProvisioningService.class.getAnnotation(Transactional.class).value());
        for (String method : List.of("prepare", "reconcile"))
            assertEquals(Transactional.TxType.REQUIRES_NEW, ProvisioningTransactions.class.getMethod(method, ProvisioningCommand.class)
                .getAnnotation(Transactional.class).value());
        var f = new ReceiverFixture(); var tx = mock(ProvisioningTransactions.class); var cfg = mock(AcademiaProvisioningConfiguration.class);
        when(cfg.loadEnabled()).thenReturn(f.identity);
        var service = new ClassroomProvisioningService(tx, cfg);
        when(tx.prepare(any())).thenThrow(new PersistenceException("PRIVATE"));
        expect(TEMPORARILY_UNAVAILABLE, () -> service.prepare(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), f.request()));
        verify(tx, never()).reconcile(any());
        doThrow(Database.duplicate()).when(tx).prepare(any());
        doThrow(Database.duplicate()).when(tx).reconcile(any());
        expect(TEMPORARILY_UNAVAILABLE, () -> service.prepare(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString(), f.request()));
        var order = inOrder(tx);
        order.verify(tx, times(2)).prepare(any()); order.verify(tx).reconcile(any());
    }

    @Test void terminalFailedReceiptCanBeRecoveredButDoesNotCreateAnAulaOrConvertAnIncidentToFailed() {
        var f = new ReceiverFixture(); var db = new Database(f);
        var receipt = ClassroomProvisioningReceipt.failed(f.portal, f.operation, f.classroom, f.academy,
            f.command().fingerprint(), ProvisioningErrorCode.PREPARATION_REJECTED);
        ReceiverFixture.lifecycle(receipt, PrePersist.class); db.committedReceipts.put(f.operation, receipt);
        assertEquals(422, db.put(f).httpStatus());
        assertEquals("FAILED", db.service.getOperation(f.principal, f.academy.toString(), f.classroom.toString(), f.operation.toString()).getResult());
        assertTrue(db.committedClassrooms.isEmpty());
    }
    @Test void twoConcurrentOperationsForSameClassroomCreateTwoReceiptsButOnlyOneLocalIdentity() throws Exception {
        var f = new ReceiverFixture(); var db = new Database(f); db.firstCommits = new CyclicBarrier(2);
        UUID secondOperation = UUID.randomUUID();
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> db.put(f));
            var second = threads.submit(() -> db.put(f, f.classroom, secondOperation, f.request()));
            assertEquals(Set.of(201, 200), Set.of(first.get(10, TimeUnit.SECONDS).httpStatus(), second.get(10, TimeUnit.SECONDS).httpStatus()));
        }
        assertEquals(1, db.committedClassrooms.size()); assertEquals(2, db.committedReceipts.size());
    }

    @Test void mismatchedStoredContextBlocksRequestsInsteadOfRebindingTheDatabase() {
        var f = new ReceiverFixture(); var db = new Database(f);
        when(db.classrooms.containsOtherInstance(f.portal, f.academy)).thenReturn(true);
        expect(INVALID_CONFIGURATION, () -> db.put(f));
        verify(db.classrooms, never()).create(any()); verify(db.receipts, never()).create(any());
    }
}
