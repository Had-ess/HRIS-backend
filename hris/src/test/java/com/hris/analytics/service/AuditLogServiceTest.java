package com.hris.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.AuditLogRepository;
import com.hris.common.event.ActorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


// TODO (C1 integration): Add a @DataJpaTest or @SpringBootTest test that proves rollback:
//   start a transaction, call log(), mark rollback-only, commit; assert auditLogRepository.count() == 0.
//   Requires either H2 test dependency or Testcontainers postgres fixture — deferred to a later phase.
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    // --- Contract tests: verify C1 fix is structurally correct ---

    @Test
    @DisplayName("log(6-arg) is @Transactional with default REQUIRED propagation — not @Async (C1)")
    void sixArgLog_isTransactionalRequiredNotAsync() throws Exception {
        Method method = AuditLogService.class.getMethod(
                "log", UUID.class, AuditAction.class, String.class, UUID.class, Object.class, Object.class);

        assertThat(method.isAnnotationPresent(Async.class))
                .as("log() must NOT be @Async after C1 fix")
                .isFalse();

        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("log() must be @Transactional")
                .isNotNull();
        assertThat(tx.propagation())
                .as("propagation must be REQUIRED so audit rolls back with the business tx")
                .isEqualTo(Propagation.REQUIRED);
    }

    @Test
    @DisplayName("log(7-arg) is @Transactional with default REQUIRED propagation — not @Async (C1)")
    void sevenArgLog_isTransactionalRequiredNotAsync() throws Exception {
        Method method = AuditLogService.class.getMethod(
                "log", UUID.class, ActorType.class, AuditAction.class, String.class,
                UUID.class, Object.class, Object.class);

        assertThat(method.isAnnotationPresent(Async.class))
                .as("log() must NOT be @Async after C1 fix")
                .isFalse();

        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("log() must be @Transactional")
                .isNotNull();
        assertThat(tx.propagation())
                .as("propagation must be REQUIRED so audit rolls back with the business tx")
                .isEqualTo(Propagation.REQUIRED);
    }

    // --- Behavioral tests ---

    @Test
    @DisplayName("log persists an audit entry synchronously (C1)")
    void log_persistsEntrySynchronously() {
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // "data" is a String — serializeState returns it directly, no ObjectMapper call needed
        auditLogService.log(actorId, AuditAction.CREATE, "EMPLOYEE", resourceId, null, "data");

        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("repository exception is caught internally and does not escape to caller (C1)")
    void log_repositoryExceptionCaughtInternally() {
        // The catch block in log() ensures the caller is not disrupted by audit failures.
        // With @Async removed, any exception would propagate if not caught — the catch block
        // ensures graceful degradation while still being in the same transaction.
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db error"));

        UUID actorId = UUID.randomUUID();
        auditLogService.log(actorId, AuditAction.CREATE, "EMPLOYEE", UUID.randomUUID(), null, null);
        verify(auditLogRepository, times(1)).save(any());
    }
}
