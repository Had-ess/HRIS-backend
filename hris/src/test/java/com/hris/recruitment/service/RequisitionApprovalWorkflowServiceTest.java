package com.hris.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.recruitment.entity.Requisition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequisitionApprovalWorkflowServiceTest {

    @Mock private ApprovalWorkflowRepository approvalWorkflowRepository;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private RequisitionApprovalWorkflowService service;

    private final UUID deptId = UUID.randomUUID();
    private final UUID hmEmployeeId = UUID.randomUUID();
    private final UUID hmUserId = UUID.randomUUID();

    private Requisition requisition() {
        return Requisition.builder()
            .id(UUID.randomUUID()).title("Role").departmentId(deptId)
            .hiringManagerEmployeeId(hmEmployeeId).build();
    }

    private Employee hiringManager() {
        return Employee.builder().id(hmEmployeeId).userId(hmUserId).build();
    }

    private User activeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setActive(true);
        return u;
    }

    @Test
    void instantiate_resolvesDepartmentHeadAsApprover() {
        UUID headEmployeeId = UUID.randomUUID();
        UUID headUserId = UUID.randomUUID();
        Department dept = Department.builder().id(deptId).headEmployeeId(headEmployeeId).build();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(employeeRepository.findById(headEmployeeId)).thenReturn(Optional.of(
            Employee.builder().id(headEmployeeId).userId(headUserId).build()));
        when(userRepository.findById(headUserId)).thenReturn(Optional.of(activeUser(headUserId)));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(i -> {
            ApprovalWorkflow w = i.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        ApprovalWorkflow workflow = service.instantiate(requisition(), hiringManager());

        assertThat(workflow.getSubjectType()).isEqualTo("REQUISITION");
        assertThat(workflow.getRequiredApprovals()).isEqualTo(1);

        ArgumentCaptor<List<ApprovalStep>> captor = ArgumentCaptor.forClass(List.class);
        verify(approvalStepRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getApproverId()).isEqualTo(headUserId);
    }

    @Test
    void instantiate_fallsBackToHrApprovers() {
        Department dept = Department.builder().id(deptId).headEmployeeId(null).build();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        UUID hrUserId = UUID.randomUUID();
        when(userRepository.findByPermissionNames(List.of("RECRUITMENT_APPROVE")))
            .thenReturn(List.of(activeUser(hrUserId)));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(i -> {
            ApprovalWorkflow w = i.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        ApprovalWorkflow workflow = service.instantiate(requisition(), hiringManager());

        assertThat(workflow.getRequiredApprovals()).isEqualTo(1);
        ArgumentCaptor<List<ApprovalStep>> captor = ArgumentCaptor.forClass(List.class);
        verify(approvalStepRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getApproverId()).isEqualTo(hrUserId);
    }

    @Test
    void instantiate_blocksWhenNoApproverResolves() {
        Department dept = Department.builder().id(deptId).headEmployeeId(null).build();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        when(userRepository.findByPermissionNames(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.instantiate(requisition(), hiringManager()))
            .isInstanceOf(InvalidWorkflowStateException.class);
        verify(approvalWorkflowRepository, never()).save(any());
    }

    @Test
    void instantiate_excludesHiringManagerWhoIsAlsoDeptHead() {
        // Hiring manager heads their own department → must escalate to HR fallback.
        Department dept = Department.builder().id(deptId).headEmployeeId(hmEmployeeId).build();
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));
        UUID hrUserId = UUID.randomUUID();
        when(userRepository.findByPermissionNames(List.of("RECRUITMENT_APPROVE")))
            .thenReturn(List.of(activeUser(hrUserId)));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(i -> {
            ApprovalWorkflow w = i.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        ApprovalWorkflow workflow = service.instantiate(requisition(), hiringManager());

        ArgumentCaptor<List<ApprovalStep>> captor = ArgumentCaptor.forClass(List.class);
        verify(approvalStepRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getApproverId()).isEqualTo(hrUserId);
    }
}
