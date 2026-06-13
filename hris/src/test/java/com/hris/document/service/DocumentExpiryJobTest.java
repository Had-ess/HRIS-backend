package com.hris.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.document.entity.EmployeeDocument;
import com.hris.document.enums.DocumentType;
import com.hris.document.repository.EmployeeDocumentRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.tenancy.TenantJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExpiryJobTest {

    @Mock private EmployeeDocumentRepository documentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Mock private TenantJobRunner tenantJobRunner;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DocumentExpiryJob job;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID ownerUserId = UUID.randomUUID();
    private Employee employee;
    private User owner;
    private User hrUser;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
            .id(employeeId).userId(ownerUserId).employeeCode("EMP-1")
            .hireDate(LocalDate.of(2024, 1, 1))
            .status(EmployeeStatus.ACTIVE).contractType(ContractType.PERMANENT)
            .build();
        owner = User.builder().id(ownerUserId).email("o@x").firstName("Own").lastName("Er").build();
        hrUser = User.builder().id(UUID.randomUUID()).email("hr@x").firstName("Aitch").lastName("Arr").build();

        lenient().when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        lenient().when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        lenient().when(userRepository.findByPermissionNames(any())).thenReturn(List.of(hrUser));
        lenient().when(documentRepository.save(any(EmployeeDocument.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private EmployeeDocument document(LocalDate expiry) {
        return EmployeeDocument.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .docType(DocumentType.WORK_PERMIT).title("Permit")
            .fileName("p.pdf").mimeType("application/pdf").storagePath("k").sizeBytes(1)
            .expiryDate(expiry)
            .build();
    }

    @Test
    void expiringDocumentNotifiesOwnerAndHrAndStamps() {
        EmployeeDocument doc = document(LocalDate.now().plusDays(10));
        when(documentRepository.findByExpiryDateLessThanEqualAndExpiryNotifiedAtIsNull(any()))
            .thenReturn(List.of(doc));

        int alerts = job.sweepExpiringDocuments();

        assertThat(alerts).isEqualTo(2);
        assertThat(doc.getExpiryNotifiedAt()).isNotNull();

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher, times(2)).publishAfterCommit(captor.capture());
        assertThat(captor.getAllValues())
            .allSatisfy(e -> assertThat(e.getEventType()).isEqualTo(NotificationEventType.DOCUMENT_EXPIRING));
        assertThat(captor.getAllValues())
            .extracting(NotificationEvent::getTargetUserId)
            .containsExactlyInAnyOrder(ownerUserId, hrUser.getId());
    }

    @Test
    void alreadyExpiredDocumentUsesExpiredEvent() {
        EmployeeDocument doc = document(LocalDate.now().minusDays(2));
        when(documentRepository.findByExpiryDateLessThanEqualAndExpiryNotifiedAtIsNull(any()))
            .thenReturn(List.of(doc));

        job.sweepExpiringDocuments();

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher, times(2)).publishAfterCommit(captor.capture());
        assertThat(captor.getAllValues())
            .allSatisfy(e -> assertThat(e.getEventType()).isEqualTo(NotificationEventType.DOCUMENT_EXPIRED));
    }

    @Test
    void documentsWithoutDueExpiryProduceNoAlerts() {
        when(documentRepository.findByExpiryDateLessThanEqualAndExpiryNotifiedAtIsNull(any()))
            .thenReturn(List.of());

        assertThat(job.sweepExpiringDocuments()).isZero();
    }

    @Test
    void hrUserWhoIsAlsoOwnerIsNotNotifiedTwice() {
        when(userRepository.findByPermissionNames(any())).thenReturn(List.of(owner));
        EmployeeDocument doc = document(LocalDate.now().plusDays(5));
        when(documentRepository.findByExpiryDateLessThanEqualAndExpiryNotifiedAtIsNull(any()))
            .thenReturn(List.of(doc));

        int alerts = job.sweepExpiringDocuments();

        assertThat(alerts).isEqualTo(1);
        verify(notificationPublisher, times(1)).publishAfterCommit(any());
    }
}
