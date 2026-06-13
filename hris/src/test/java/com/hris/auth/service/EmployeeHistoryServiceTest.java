package com.hris.auth.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.EmployeeDepartmentHistory;
import com.hris.auth.entity.EmployeeStatusHistory;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeDepartmentHistoryRepository;
import com.hris.auth.repository.EmployeeStatusHistoryRepository;
import com.hris.common.event.SystemActor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Regression guard for the scheduled-job actor bug: the daily lifecycle job
 * executes transfers/terminations as {@link SystemActor#SYSTEM_ACTOR_ID}, whose
 * all-zeros sentinel is not a real user. Persisting it into the {@code changed_by}
 * column (FK to users) rolled back the whole job transaction, so every scheduled
 * transfer/termination silently failed. The system actor must be stored as null.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeHistoryServiceTest {

    @Mock
    private EmployeeStatusHistoryRepository statusHistoryRepository;

    @Mock
    private EmployeeDepartmentHistoryRepository departmentHistoryRepository;

    @InjectMocks
    private EmployeeHistoryService historyService;

    private Employee employee(UUID deptId, EmployeeStatus status) {
        return Employee.builder()
            .id(UUID.randomUUID())
            .hireDate(LocalDate.now())
            .status(status)
            .departmentId(deptId)
            .build();
    }

    @Test
    @DisplayName("department transfer by the system actor stores changed_by = null")
    void departmentTransfer_systemActor_storesNullChangedBy() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Employee previous = employee(from, EmployeeStatus.ACTIVE);
        Employee current = employee(to, EmployeeStatus.ACTIVE);
        current.setId(previous.getId());

        historyService.recordDepartmentTransfer(previous, current, SystemActor.SYSTEM_ACTOR_ID, LocalDate.now());

        ArgumentCaptor<EmployeeDepartmentHistory> captor =
            ArgumentCaptor.forClass(EmployeeDepartmentHistory.class);
        verify(departmentHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isNull();
    }

    @Test
    @DisplayName("department transfer by a real user stores the actor id verbatim")
    void departmentTransfer_realUser_storesActorId() {
        UUID actor = UUID.randomUUID();
        Employee previous = employee(UUID.randomUUID(), EmployeeStatus.ACTIVE);
        Employee current = employee(UUID.randomUUID(), EmployeeStatus.ACTIVE);
        current.setId(previous.getId());

        historyService.recordDepartmentTransfer(previous, current, actor, LocalDate.now());

        ArgumentCaptor<EmployeeDepartmentHistory> captor =
            ArgumentCaptor.forClass(EmployeeDepartmentHistory.class);
        verify(departmentHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isEqualTo(actor);
    }

    @Test
    @DisplayName("status change by the system actor stores changed_by = null")
    void statusChange_systemActor_storesNullChangedBy() {
        UUID dept = UUID.randomUUID();
        Employee previous = employee(dept, EmployeeStatus.ACTIVE);
        Employee current = employee(dept, EmployeeStatus.TERMINATED);
        current.setId(previous.getId());

        historyService.recordStatusChange(previous, current, SystemActor.SYSTEM_ACTOR_ID,
            LocalDate.now(), "SCHEDULED_TERMINATION");

        ArgumentCaptor<EmployeeStatusHistory> captor =
            ArgumentCaptor.forClass(EmployeeStatusHistory.class);
        verify(statusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isNull();
    }
}
