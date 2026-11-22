package academy.llavor.academia.provisioning;

import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.classroom.repository.LocalClassroomRepository;
import academy.llavor.academia.provisioning.entity.ClassroomProvisioningReceipt;
import academy.llavor.academia.provisioning.repository.ClassroomProvisioningReceiptRepository;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiverRepositoryTest {
    @SuppressWarnings("unchecked")
    @Test void classroomQueriesAreBoundedAndScopedToPortalAndCodeOrIdentity() {
        var em = mock(EntityManager.class); var repo = new LocalClassroomRepository(); ReceiverFixture.field(repo, "em", em);
        TypedQuery<LocalClassroom> rows = mock(TypedQuery.class, RETURNS_SELF);
        TypedQuery<Long> count = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(LocalClassroom.class))).thenReturn(rows);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        when(rows.getResultList()).thenReturn(List.of()); when(count.getSingleResult()).thenReturn(0L);
        var f = new ReceiverFixture();
        assertTrue(repo.findByIdentity(f.portal, f.classroom).isEmpty());
        assertTrue(repo.findByCode(f.portal, "AULA_001").isEmpty());
        assertFalse(repo.containsOtherInstance(f.portal, f.academy));
        verify(rows, times(2)).setParameter("portal", f.portal); verify(rows).setParameter("classroom", f.classroom);
        verify(rows).setParameter("code", "AULA_001"); verify(rows, times(2)).setMaxResults(1);
        assertThrows(IllegalArgumentException.class, () -> repo.create(null));
    }

    @SuppressWarnings("unchecked")
    @Test void receiptQueriesAreScopedAndWritesRequireExistingAcademiaTransactionWithoutMerge() throws Exception {
        var em = mock(EntityManager.class); var repo = new ClassroomProvisioningReceiptRepository(); ReceiverFixture.field(repo, "em", em);
        TypedQuery<ClassroomProvisioningReceipt> rows = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(ClassroomProvisioningReceipt.class))).thenReturn(rows);
        when(rows.getResultList()).thenReturn(List.of()); var f = new ReceiverFixture();
        assertTrue(repo.findByOperation(f.portal, f.operation).isEmpty());
        verify(rows).setParameter("portal", f.portal); verify(rows).setParameter("operation", f.operation);
        verify(rows).setMaxResults(1);
        var local = new LocalClassroom(f.portal, f.classroom, f.academy, "A", "Aula"); ReceiverFixture.field(local, "id", 1L);
        var receipt = ClassroomProvisioningReceipt.ready(f.operation, f.command().fingerprint(), local);
        repo.create(receipt);
        var order = inOrder(em); order.verify(em).createQuery(anyString(), eq(ClassroomProvisioningReceipt.class));
        order.verify(em).persist(receipt); order.verify(em).flush();
        verify(em, never()).merge(any());
        for (Class<?> repository : List.of(LocalClassroomRepository.class, ClassroomProvisioningReceiptRepository.class)) {
            assertEquals("LlavorAcademiaPU", repository.getDeclaredField("em").getAnnotation(PersistenceContext.class).unitName());
            var method = Arrays.stream(repository.getMethods()).filter(m -> m.getName().equals("create")).findFirst().orElseThrow();
            assertEquals(Transactional.TxType.MANDATORY, method.getAnnotation(Transactional.class).value());
        }
    }
}
