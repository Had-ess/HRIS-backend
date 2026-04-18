package com.hris.leave.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.ApprovalRouter;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.common.exception.InsufficientLeaveBalanceException;
import com.hris.common.exception.InvalidLeavePeriodException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.leave.dto.CreateLeaveRequestDto;
import com.hris.leave.service.AttachmentDownload;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.FileAttachment;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.leave.repository.FileAttachmentRepository;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.service.NotificationPublisher;
import com.hris.organisation.service.WorkScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveRequestService Unit Tests")
class LeaveRequestServiceTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private ApprovalWorkflowRepository approvalWorkflowRepository;
    @Mock private ApprovalRouter approvalRouter;
    @Mock private WorkScheduleService workScheduleService;
    @Mock private FileStorageService fileStorageService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectMapper objectMapper;
    @Mock private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private UUID requesterId;
    private UUID employeeId;
    private UUID leaveTypeId;
    private UUID scheduleId;
    private Employee employee;
    private User requesterUser;
    private LeaveType leaveType;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        leaveTypeId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();

        employee = Employee.builder()
            .id(employeeId)
            .userId(requesterId)
            .workScheduleId(scheduleId)
            .build();

        requesterUser = User.builder()
            .id(requesterId)
            .firstName("John")
            .lastName("Doe")
            .build();

        leaveType = LeaveType.builder()
            .id(leaveTypeId)
            .name("Annual Leave")
            .requiresJustification(false)
            .build();

        lenient().when(fileStorageService.sanitizeFilename(anyString())).thenAnswer(invocation -> {
            String filename = invocation.getArgument(0);
            return filename == null ? "unknown" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        });
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should use leave request start year for balance lookup when creating")
        void shouldUseStartYearForBalanceLookup_WhenCreatingRequest() throws Exception {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2027, 6, 1),
                LocalDate.of(2027, 6, 5),
                UrgencyLevel.NORMAL,
                "Family vacation"
            );

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2027)
                .totalDays(20)
                .usedDays(0)
                .pendingDays(0)
                .build();

            UUID savedRequestId = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
            when(workScheduleService.computeWorkingDays(any(), any(), eq(scheduleId))).thenReturn(5);
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2027)).thenReturn(Optional.of(balance));
            when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
                LeaveRequest request = inv.getArgument(0);
                request.setId(savedRequestId);
                return request;
            });
            when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(inv -> {
                ApprovalWorkflow workflow = inv.getArgument(0);
                workflow.setId(workflowId);
                return workflow;
            });
            when(approvalRouter.resolveSteps(eq(employeeId), eq(workflowId), any(), any()))
                .thenReturn(List.of(ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflowId(workflowId)
                    .approverId(UUID.randomUUID())
                    .status(StepStatus.PENDING)
                    .build()));
            when(userRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
                UUID userId = invocation.getArgument(0);
                if (requesterId.equals(userId)) {
                    return Optional.of(requesterUser);
                }
                return Optional.of(User.builder()
                    .id(userId)
                    .firstName("Approver")
                    .lastName("User")
                    .localePreference("en")
                    .build());
            });
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            LeaveRequest result = leaveRequestService.create(dto, requesterId);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(LeaveStatus.IN_APPROVAL);
            verify(leaveBalanceRepository).findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2027);
        }

        @Test
        @DisplayName("should create leave request and deduct balance when sufficient days available")
        void shouldCreateLeaveRequest_WhenBalanceSufficient() throws Exception {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2027, 6, 1),
                LocalDate.of(2027, 6, 5),
                UrgencyLevel.NORMAL,
                "Family vacation"
            );

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2027)
                .totalDays(20)
                .usedDays(0)
                .pendingDays(0)
                .build();

            UUID savedRequestId = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
            when(workScheduleService.computeWorkingDays(any(), any(), eq(scheduleId))).thenReturn(5);
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2027)).thenReturn(Optional.of(balance));
            when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
                LeaveRequest request = inv.getArgument(0);
                request.setId(savedRequestId);
                return request;
            });
            when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(inv -> {
                ApprovalWorkflow workflow = inv.getArgument(0);
                workflow.setId(workflowId);
                return workflow;
            });
            when(approvalRouter.resolveSteps(eq(employeeId), eq(workflowId), any(), any()))
                .thenReturn(List.of(ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflowId(workflowId)
                    .approverId(UUID.randomUUID())
                    .status(StepStatus.PENDING)
                    .build()));
            when(userRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
                UUID userId = invocation.getArgument(0);
                if (requesterId.equals(userId)) {
                    return Optional.of(requesterUser);
                }
                return Optional.of(User.builder()
                    .id(userId)
                    .firstName("Approver")
                    .lastName("User")
                    .localePreference("en")
                    .build());
            });
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            LeaveRequest result = leaveRequestService.create(dto, requesterId);

            assertThat(result).isNotNull();
            assertThat(result.getEmployeeId()).isEqualTo(employeeId);
            assertThat(result.getLeaveTypeId()).isEqualTo(leaveTypeId);
            assertThat(result.getWorkingDays()).isEqualTo(5);
            assertThat(result.getStatus()).isEqualTo(LeaveStatus.IN_APPROVAL);
            assertThat(balance.getPendingDays()).isEqualTo(5);

            verify(leaveBalanceRepository).findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2027);
            verify(leaveBalanceRepository).save(balance);
        }

        @Test
        @DisplayName("should throw InsufficientLeaveBalanceException when balance too low")
        void shouldThrow_WhenInsufficientBalance() {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 10),
                UrgencyLevel.NORMAL,
                null
            );

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2026)
                .totalDays(3)
                .usedDays(0)
                .pendingDays(0)
                .build();

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
            when(workScheduleService.computeWorkingDays(any(), any(), eq(scheduleId))).thenReturn(8);
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2026)).thenReturn(Optional.of(balance));

            assertThatThrownBy(() -> leaveRequestService.create(dto, requesterId))
                .isInstanceOf(InsufficientLeaveBalanceException.class)
                .hasMessageContaining("Insufficient balance");
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when employee not found")
        void shouldThrow_WhenEmployeeNotFound() {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                UrgencyLevel.NORMAL,
                null
            );

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.create(dto, requesterId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Employee not found");
        }

        @Test
        @DisplayName("should throw when no balance record exists for leave type")
        void shouldThrow_WhenNoBalanceRecord() {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                UrgencyLevel.NORMAL,
                null
            );

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
            when(workScheduleService.computeWorkingDays(any(), any(), eq(scheduleId))).thenReturn(5);
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2026)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.create(dto, requesterId))
                .isInstanceOf(InsufficientLeaveBalanceException.class)
                .hasMessage("No balance found for this leave type");
        }

        @Test
        @DisplayName("should fail fast when no approvers can be resolved")
        void shouldThrow_WhenNoApproversResolved() {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3),
                UrgencyLevel.NORMAL,
                "Vacation"
            );

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2026)
                .totalDays(20)
                .usedDays(0)
                .pendingDays(0)
                .build();

            UUID workflowId = UUID.randomUUID();

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
            when(workScheduleService.computeWorkingDays(any(), any(), eq(scheduleId))).thenReturn(3);
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2026)).thenReturn(Optional.of(balance));
            when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
                LeaveRequest request = inv.getArgument(0);
                request.setId(UUID.randomUUID());
                return request;
            });
            when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(inv -> {
                ApprovalWorkflow workflow = inv.getArgument(0);
                workflow.setId(workflowId);
                return workflow;
            });
            when(approvalRouter.resolveSteps(eq(employeeId), eq(workflowId), any(), any()))
                .thenReturn(List.of());

            assertThatThrownBy(() -> leaveRequestService.create(dto, requesterId))
                .isInstanceOf(InvalidWorkflowStateException.class)
                .hasMessage("No approvers could be resolved for this leave request");

            verify(notificationPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should reject leave request that spans multiple calendar years")
        void shouldRejectCrossYearLeaveRequest() {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto(
                leaveTypeId,
                LocalDate.of(2026, 12, 30),
                LocalDate.of(2027, 1, 2),
                UrgencyLevel.NORMAL,
                null
            );

            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.create(dto, requesterId))
                .isInstanceOf(InvalidLeavePeriodException.class)
                .hasMessage("Leave request cannot span multiple calendar years");
        }
    }

    @Nested
    @DisplayName("cancel()")
    class CancelTests {

        @Test
        @DisplayName("should use request start year when cancelling")
        void shouldUseRequestYear_WhenCancellingPendingRequest() {
            UUID requestId = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .workingDays(3)
                .startDate(LocalDate.of(2027, 6, 1))
                .endDate(LocalDate.of(2027, 6, 3))
                .status(LeaveStatus.PENDING)
                .build();

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2027)
                .totalDays(20)
                .usedDays(0)
                .pendingDays(3)
                .build();

            ApprovalStep pendingStep = ApprovalStep.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .approverId(UUID.randomUUID())
                .status(StepStatus.PENDING)
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2027)).thenReturn(Optional.of(balance));
            when(approvalWorkflowRepository.findBySubjectTypeAndSubjectIdForUpdate("LEAVE", requestId))
                .thenReturn(Optional.of(ApprovalWorkflow.builder()
                    .id(workflowId)
                    .subjectType("LEAVE")
                    .subjectId(requestId)
                    .status(WorkflowStatus.IN_PROGRESS)
                    .build()));
            when(approvalStepRepository.findByWorkflowIdAndStatus(workflowId, StepStatus.PENDING))
                .thenReturn(List.of(pendingStep));

            leaveRequestService.cancel(requestId, requesterId);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
            assertThat(balance.getPendingDays()).isEqualTo(0);
            assertThat(pendingStep.getStatus()).isEqualTo(StepStatus.REJECTED);
            assertThat(pendingStep.getComment()).isEqualTo("Auto-closed due to cancellation");
            assertThat(pendingStep.getDecidedAt()).isNotNull();

            verify(leaveRequestRepository).save(request);
            verify(leaveBalanceRepository).findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2027);
            verify(leaveBalanceRepository).save(balance);
            verify(approvalStepRepository).saveAll(List.of(pendingStep));
        }

        @Test
        @DisplayName("should throw when trying to cancel an approved request")
        void shouldThrow_WhenCancellingApprovedRequest() {
            UUID requestId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 2))
                .status(LeaveStatus.APPROVED)
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(approvalWorkflowRepository.findBySubjectTypeAndSubjectIdForUpdate("LEAVE", requestId))
                .thenReturn(Optional.empty());
            when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> leaveRequestService.cancel(requestId, requesterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot cancel an approved leave request");
        }

        @Test
        @DisplayName("should throw AccessDeniedException when non-owner tries to cancel")
        void shouldThrow_WhenNotOwner() {
            UUID requestId = UUID.randomUUID();
            UUID otherEmployeeId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(otherEmployeeId)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 2))
                .status(LeaveStatus.PENDING)
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.cancel(requestId, requesterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not your leave request");
        }
    }

    @Nested
    @DisplayName("handleWorkflowCompletion()")
    class WorkflowCompletionTests {

        @Test
        @DisplayName("should use request start year when approving workflow completion")
        void shouldUseRequestYear_WhenWorkflowCompletes() throws Exception {
            UUID requestId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .workingDays(5)
                .startDate(LocalDate.of(2028, 6, 1))
                .endDate(LocalDate.of(2028, 6, 5))
                .status(LeaveStatus.IN_APPROVAL)
                .build();

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2028)
                .totalDays(20)
                .usedDays(0)
                .pendingDays(5)
                .build();

            when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2028)).thenReturn(Optional.of(balance));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(userRepository.findById(requesterId)).thenReturn(Optional.of(requesterUser));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            leaveRequestService.handleWorkflowCompletion(requestId, WorkflowStatus.COMPLETED);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
            assertThat(balance.getUsedDays()).isEqualTo(5);
            assertThat(balance.getPendingDays()).isEqualTo(0);

            verify(leaveBalanceRepository).findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2028);
            verify(notificationPublisher).publish(any());
        }

        @Test
        @DisplayName("should reject leave and restore balance when workflow rejected")
        void shouldRejectLeave_WhenWorkflowRejected() throws Exception {
            UUID requestId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .workingDays(5)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 5))
                .status(LeaveStatus.IN_APPROVAL)
                .build();

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(2026)
                .totalDays(20)
                .usedDays(0)
                .pendingDays(5)
                .build();

            when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employeeId, leaveTypeId, 2026)).thenReturn(Optional.of(balance));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(userRepository.findById(requesterId)).thenReturn(Optional.of(requesterUser));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            leaveRequestService.handleWorkflowCompletion(requestId, WorkflowStatus.REJECTED);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
            assertThat(balance.getPendingDays()).isEqualTo(0);
            assertThat(balance.getUsedDays()).isEqualTo(0);

            verify(notificationPublisher).publish(any());
        }

        @Test
        @DisplayName("should not overwrite cancelled leave request on workflow completion")
        void shouldIgnoreWorkflowCompletion_WhenCancelled() {
            UUID requestId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .workingDays(5)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 5))
                .status(LeaveStatus.CANCELLED)
                .build();

            when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            leaveRequestService.handleWorkflowCompletion(requestId, WorkflowStatus.COMPLETED);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
            verify(leaveBalanceRepository, never()).findByEmployeeIdAndLeaveTypeIdAndYear(any(), any(), anyInt());
            verify(notificationPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("should return leave request when found")
        void shouldReturn_WhenFound() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(userRoleRepository.findByUserIdAndIsActiveTrue(requesterId)).thenReturn(List.of());

            LeaveRequest result = leaveRequestService.getById(requestId, requesterId);

            assertThat(result.getId()).isEqualTo(requestId);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when not found")
        void shouldThrow_WhenNotFound() {
            UUID requestId = UUID.randomUUID();
            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.getById(requestId, requesterId))
                .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should block non-owner non-admin from reading leave request")
        void shouldThrowAccessDenied_WhenNotOwnerOrAdmin() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(UUID.randomUUID())
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRoleRepository.findByUserIdAndIsActiveTrue(requesterId)).thenReturn(List.of());
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.getById(requestId, requesterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not allowed to access this leave request");
        }
    }

    @Nested
    @DisplayName("uploadAttachment()")
    class UploadAttachmentTests {

        @Test
        @DisplayName("should reject unsupported MIME type")
        void shouldThrow_WhenMimeTypeUnsupported() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .status(LeaveStatus.PENDING)
                .build();
            MultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes());

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.uploadAttachment(requestId, file, requesterId))
                .isInstanceOf(FileAttachmentValidationException.class)
                .hasMessage("Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");
        }

        @Test
        @DisplayName("should reject oversized attachment")
        void shouldThrow_WhenFileTooLarge() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .status(LeaveStatus.IN_APPROVAL)
                .build();
            byte[] payload = new byte[(10 * 1024 * 1024) + 1];
            payload[0] = 0x25;
            payload[1] = 0x50;
            payload[2] = 0x44;
            payload[3] = 0x46;
            payload[4] = 0x2D;
            MultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", payload);

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.uploadAttachment(requestId, file, requesterId))
                .isInstanceOf(FileAttachmentValidationException.class)
                .hasMessage("Attachment exceeds the maximum allowed size of 10 MB");
        }

        @Test
        @DisplayName("should reject disguised attachment when bytes do not match allowed content")
        void shouldThrow_WhenAttachmentContentDoesNotMatchAllowedType() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .status(LeaveStatus.PENDING)
                .build();
            MultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", "not-a-real-pdf".getBytes());

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.uploadAttachment(requestId, file, requesterId))
                .isInstanceOf(FileAttachmentValidationException.class)
                .hasMessage("Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");

            verify(fileStorageService, never()).store(any(MultipartFile.class), eq(requestId));
        }

        @Test
        @DisplayName("should reject attachment upload from non-owner")
        void shouldThrow_WhenUploaderDoesNotOwnRequest() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(UUID.randomUUID())
                .status(LeaveStatus.PENDING)
                .build();
            MultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", "pdf".getBytes());

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.uploadAttachment(requestId, file, requesterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not allowed to upload attachments for this leave request");
        }

        @Test
        @DisplayName("should reject attachment upload when request is already completed")
        void shouldThrow_WhenUploadingForInvalidState() {
            UUID requestId = UUID.randomUUID();
            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(employeeId)
                .status(LeaveStatus.APPROVED)
                .build();
            MultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", "pdf".getBytes());

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> leaveRequestService.uploadAttachment(requestId, file, requesterId))
                .isInstanceOf(InvalidWorkflowStateException.class)
                .hasMessage("Attachments can only be uploaded for leave requests that are pending approval");
        }
    }

    @Nested
    @DisplayName("downloadAttachment()")
    class DownloadAttachmentTests {

        @Test
        @DisplayName("should deny attachment access to unrelated user")
        void shouldDenyUnauthorizedAttachmentAccess() {
            UUID requestId = UUID.randomUUID();
            UUID attachmentId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(UUID.randomUUID())
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRoleRepository.findByUserIdAndIsActiveTrue(requesterId)).thenReturn(List.of());
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", requestId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.downloadAttachment(requestId, attachmentId, requesterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not allowed to access attachments for this leave request");
        }

        @Test
        @DisplayName("should allow attachment access to assigned approver")
        void shouldAllowAttachmentAccessForAssignedApprover() {
            UUID requestId = UUID.randomUUID();
            UUID attachmentId = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(UUID.randomUUID())
                .build();
            FileAttachment attachment = FileAttachment.builder()
                .id(attachmentId)
                .requestId(requestId)
                .fileName("medical_note.pdf")
                .mimeType("application/pdf")
                .storagePath(requestId + "/stored.pdf")
                .uploadedById(UUID.randomUUID())
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRoleRepository.findByUserIdAndIsActiveTrue(requesterId)).thenReturn(List.of());
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", requestId))
                .thenReturn(Optional.of(ApprovalWorkflow.builder()
                    .id(workflowId)
                    .subjectType("LEAVE")
                    .subjectId(requestId)
                    .build()));
            when(approvalStepRepository.findByWorkflowId(workflowId)).thenReturn(List.of(
                ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflowId(workflowId)
                    .approverId(requesterId)
                    .status(StepStatus.PENDING)
                    .build()
            ));
            when(fileAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
            when(fileStorageService.retrieve(attachment.getStoragePath()))
                .thenReturn(new ByteArrayInputStream("pdf".getBytes()));

            AttachmentDownload result = leaveRequestService.downloadAttachment(requestId, attachmentId, requesterId);

            assertThat(result.fileName()).isEqualTo("medical_note.pdf");
            assertThat(result.mimeType()).isEqualTo("application/pdf");
            assertThat(result.resource()).isNotNull();
        }

        @Test
        @DisplayName("should deny attachment access to approver whose step is no longer pending")
        void shouldDenyAttachmentAccessForFormerApprover() {
            UUID requestId = UUID.randomUUID();
            UUID attachmentId = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();

            LeaveRequest request = LeaveRequest.builder()
                .id(requestId)
                .employeeId(UUID.randomUUID())
                .build();

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRoleRepository.findByUserIdAndIsActiveTrue(requesterId)).thenReturn(List.of());
            when(employeeRepository.findByUserId(requesterId)).thenReturn(Optional.of(employee));
            when(approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", requestId))
                .thenReturn(Optional.of(ApprovalWorkflow.builder()
                    .id(workflowId)
                    .subjectType("LEAVE")
                    .subjectId(requestId)
                    .build()));
            when(approvalStepRepository.findByWorkflowId(workflowId)).thenReturn(List.of(
                ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflowId(workflowId)
                    .approverId(requesterId)
                    .status(StepStatus.APPROVED)
                    .build()
            ));

            assertThatThrownBy(() -> leaveRequestService.downloadAttachment(requestId, attachmentId, requesterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not allowed to access attachments for this leave request");
        }
    }
}
