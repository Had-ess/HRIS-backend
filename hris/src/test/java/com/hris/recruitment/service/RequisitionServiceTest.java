package com.hris.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.ContractType;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.compensation.repository.PayGradeRepository;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionUpdateDto;
import com.hris.recruitment.entity.Requisition;
import com.hris.recruitment.enums.RequisitionStatus;
import com.hris.recruitment.repository.ApplicationRepository;
import com.hris.recruitment.repository.RequisitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequisitionServiceTest {

    @Mock private RequisitionRepository requisitionRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private RequisitionApprovalWorkflowService approvalWorkflowService;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private ApprovalWorkflowRepository approvalWorkflowRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PayGradeRepository payGradeRepository;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private RequisitionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();
    private final UUID jobTitleId = UUID.randomUUID();
    private final UUID hmId = UUID.randomUUID();

    private Requisition draft() {
        return Requisition.builder()
            .id(UUID.randomUUID())
            .title("Backend Engineer")
            .jobTitleId(jobTitleId)
            .departmentId(deptId)
            .hiringManagerEmployeeId(hmId)
            .employmentType(ContractType.PERMANENT)
            .headcount(1)
            .filledCount(0)
            .status(RequisitionStatus.DRAFT)
            .build();
    }

    private RequisitionCreateDto createDto() {
        return new RequisitionCreateDto("Backend Engineer", jobTitleId, deptId, hmId, null,
            ContractType.PERMANENT, "Tunis", 2, "Build things");
    }

    @Test
    void create_persistsDraft() {
        when(departmentRepository.existsById(deptId)).thenReturn(true);
        when(employeeRepository.existsById(hmId)).thenReturn(true);
        when(requisitionRepository.save(any(Requisition.class))).thenAnswer(i -> {
            Requisition r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        var dto = service.create(createDto(), userId);

        assertThat(dto.status()).isEqualTo(RequisitionStatus.DRAFT);
        assertThat(dto.headcount()).isEqualTo(2);
    }

    @Test
    void update_rejectsNonDraft() {
        Requisition open = draft();
        open.setStatus(RequisitionStatus.OPEN);
        when(requisitionRepository.findById(open.getId())).thenReturn(Optional.of(open));

        RequisitionUpdateDto dto = new RequisitionUpdateDto("X", jobTitleId, deptId, hmId, null,
            ContractType.PERMANENT, null, 1, null);

        assertThatThrownBy(() -> service.update(open.getId(), dto, userId))
            .isInstanceOf(InvalidWorkflowStateException.class);
    }

    @Test
    void submit_movesToPendingApprovalAndInstantiatesWorkflow() {
        Requisition d = draft();
        when(requisitionRepository.findById(d.getId())).thenReturn(Optional.of(d));
        when(employeeRepository.findById(hmId)).thenReturn(Optional.of(
            Employee.builder().id(hmId).userId(UUID.randomUUID()).build()));
        when(approvalWorkflowService.instantiate(any(), any())).thenReturn(
            ApprovalWorkflow.builder().id(UUID.randomUUID()).build());
        when(requisitionRepository.save(any(Requisition.class))).thenAnswer(i -> i.getArgument(0));
        when(approvalWorkflowRepository.findBySubjectTypeAndSubjectId(any(), any())).thenReturn(Optional.empty());

        var dto = service.submit(d.getId(), userId);

        assertThat(dto.status()).isEqualTo(RequisitionStatus.PENDING_APPROVAL);
        verify(approvalWorkflowService).instantiate(eq(d), any());
    }

    @Test
    void submit_rejectsNonDraft() {
        Requisition d = draft();
        d.setStatus(RequisitionStatus.OPEN);
        when(requisitionRepository.findById(d.getId())).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.submit(d.getId(), userId))
            .isInstanceOf(InvalidWorkflowStateException.class);
        verify(approvalWorkflowService, never()).instantiate(any(), any());
    }

    @Test
    void recordHire_incrementsAndFlipsToFilledWhenFull() {
        Requisition open = draft();
        open.setStatus(RequisitionStatus.OPEN);
        open.setHeadcount(1);
        open.setFilledCount(0);
        when(requisitionRepository.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));
        when(requisitionRepository.save(any(Requisition.class))).thenAnswer(i -> i.getArgument(0));

        service.recordHire(open.getId(), userId);

        assertThat(open.getFilledCount()).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(RequisitionStatus.FILLED);
        assertThat(open.getClosedAt()).isNotNull();
    }

    @Test
    void recordHire_staysOpenWhenHeadcountRemains() {
        Requisition open = draft();
        open.setStatus(RequisitionStatus.OPEN);
        open.setHeadcount(3);
        open.setFilledCount(0);
        when(requisitionRepository.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));
        when(requisitionRepository.save(any(Requisition.class))).thenAnswer(i -> i.getArgument(0));

        service.recordHire(open.getId(), userId);

        assertThat(open.getFilledCount()).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(RequisitionStatus.OPEN);
    }

    @Test
    void handleWorkflowCompletion_approvedOpensRequisition() {
        Requisition d = draft();
        d.setStatus(RequisitionStatus.PENDING_APPROVAL);
        when(requisitionRepository.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        when(requisitionRepository.save(any(Requisition.class))).thenAnswer(i -> i.getArgument(0));
        when(employeeRepository.findById(hmId)).thenReturn(Optional.empty());

        service.handleWorkflowCompletion(d.getId(), WorkflowStatus.APPROVED, userId);

        assertThat(d.getStatus()).isEqualTo(RequisitionStatus.OPEN);
        assertThat(d.getOpenedAt()).isNotNull();
    }

    @Test
    void handleWorkflowCompletion_rejectedReturnsToDraft() {
        Requisition d = draft();
        d.setStatus(RequisitionStatus.PENDING_APPROVAL);
        when(requisitionRepository.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        when(requisitionRepository.save(any(Requisition.class))).thenAnswer(i -> i.getArgument(0));
        when(employeeRepository.findById(hmId)).thenReturn(Optional.empty());

        service.handleWorkflowCompletion(d.getId(), WorkflowStatus.REJECTED, userId);

        assertThat(d.getStatus()).isEqualTo(RequisitionStatus.DRAFT);
    }
}
