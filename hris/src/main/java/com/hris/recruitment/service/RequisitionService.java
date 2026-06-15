package com.hris.recruitment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.repository.PayGradeRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionDto;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionUpdateDto;
import com.hris.recruitment.entity.Requisition;
import com.hris.recruitment.enums.RequisitionStatus;
import com.hris.recruitment.repository.ApplicationRepository;
import com.hris.recruitment.repository.RequisitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequisitionService {

    private final RequisitionRepository requisitionRepository;
    private final ApplicationRepository applicationRepository;
    private final RequisitionApprovalWorkflowService approvalWorkflowService;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final PayGradeRepository payGradeRepository;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // --- Queries --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RequisitionDto> list(RequisitionStatus status, UUID departmentId) {
        List<Requisition> requisitions;
        if (status != null) {
            requisitions = requisitionRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if (departmentId != null) {
            requisitions = requisitionRepository.findByDepartmentIdOrderByCreatedAtDesc(departmentId);
        } else {
            requisitions = requisitionRepository.findAllByOrderByCreatedAtDesc();
        }
        if (departmentId != null && status != null) {
            requisitions = requisitions.stream().filter(r -> departmentId.equals(r.getDepartmentId())).toList();
        }
        return requisitions.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RequisitionDto> listMine(UUID userId) {
        Employee employee = employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found for user"));
        return requisitionRepository.findByHiringManagerEmployeeIdOrderByCreatedAtDesc(employee.getId())
            .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RequisitionDto get(UUID id) {
        return toDto(load(id));
    }

    // --- Mutations ------------------------------------------------------------

    @Transactional
    public RequisitionDto create(RequisitionCreateDto dto, UUID userId) {
        validateReferences(dto.jobTitleId(), dto.departmentId(), dto.hiringManagerEmployeeId(), dto.payGradeId());
        Requisition requisition = Requisition.builder()
            .title(dto.title())
            .jobTitleId(dto.jobTitleId())
            .departmentId(dto.departmentId())
            .hiringManagerEmployeeId(dto.hiringManagerEmployeeId())
            .payGradeId(dto.payGradeId())
            .employmentType(dto.employmentType())
            .location(dto.location())
            .headcount(dto.headcount() < 1 ? 1 : dto.headcount())
            .filledCount(0)
            .description(dto.description())
            .status(RequisitionStatus.DRAFT)
            .createdById(userId)
            .build();
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.CREATE, "recruitment_requisition", requisition.getId(), null, requisition);
        return toDto(requisition);
    }

    @Transactional
    public RequisitionDto update(UUID id, RequisitionUpdateDto dto, UUID userId) {
        Requisition requisition = load(id);
        if (requisition.getStatus() != RequisitionStatus.DRAFT) {
            throw new InvalidWorkflowStateException("Only DRAFT requisitions can be edited");
        }
        validateReferences(dto.jobTitleId(), dto.departmentId(), dto.hiringManagerEmployeeId(), dto.payGradeId());
        requisition.setTitle(dto.title());
        requisition.setJobTitleId(dto.jobTitleId());
        requisition.setDepartmentId(dto.departmentId());
        requisition.setHiringManagerEmployeeId(dto.hiringManagerEmployeeId());
        requisition.setPayGradeId(dto.payGradeId());
        requisition.setEmploymentType(dto.employmentType());
        requisition.setLocation(dto.location());
        requisition.setHeadcount(dto.headcount() < 1 ? 1 : dto.headcount());
        requisition.setDescription(dto.description());
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_requisition", requisition.getId(), null, requisition);
        return toDto(requisition);
    }

    @Transactional
    public RequisitionDto submit(UUID id, UUID userId) {
        Requisition requisition = load(id);
        if (requisition.getStatus() != RequisitionStatus.DRAFT) {
            throw new InvalidWorkflowStateException("Only DRAFT requisitions can be submitted for approval");
        }
        Employee hiringManager = employeeRepository.findById(requisition.getHiringManagerEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Hiring manager employee not found"));

        approvalWorkflowService.instantiate(requisition, hiringManager);

        requisition.setStatus(RequisitionStatus.PENDING_APPROVAL);
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.SUBMIT, "recruitment_requisition", requisition.getId(), null, requisition);
        notifyApprovers(requisition);
        return toDto(requisition);
    }

    @Transactional
    public RequisitionDto hold(UUID id, UUID userId) {
        Requisition requisition = load(id);
        if (requisition.getStatus() != RequisitionStatus.OPEN) {
            throw new InvalidWorkflowStateException("Only OPEN requisitions can be put on hold");
        }
        requisition.setStatus(RequisitionStatus.ON_HOLD);
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_requisition", requisition.getId(), null, requisition);
        return toDto(requisition);
    }

    @Transactional
    public RequisitionDto resume(UUID id, UUID userId) {
        Requisition requisition = load(id);
        if (requisition.getStatus() != RequisitionStatus.ON_HOLD) {
            throw new InvalidWorkflowStateException("Only ON_HOLD requisitions can be resumed");
        }
        requisition.setStatus(RequisitionStatus.OPEN);
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_requisition", requisition.getId(), null, requisition);
        return toDto(requisition);
    }

    @Transactional
    public RequisitionDto close(UUID id, UUID userId) {
        Requisition requisition = load(id);
        if (requisition.getStatus() != RequisitionStatus.OPEN && requisition.getStatus() != RequisitionStatus.ON_HOLD) {
            throw new InvalidWorkflowStateException("Only OPEN or ON_HOLD requisitions can be closed");
        }
        requisition.setStatus(RequisitionStatus.CLOSED);
        requisition.setClosedAt(Instant.now());
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_requisition", requisition.getId(), null, requisition);
        return toDto(requisition);
    }

    @Transactional
    public RequisitionDto cancel(UUID id, UUID userId) {
        Requisition requisition = load(id);
        if (requisition.getStatus().isTerminal()) {
            throw new InvalidWorkflowStateException("Requisition is already in a terminal state");
        }
        requisition.setStatus(RequisitionStatus.CANCELLED);
        requisition.setClosedAt(Instant.now());
        requisition = requisitionRepository.save(requisition);
        auditLogService.log(userId, AuditAction.CANCEL, "recruitment_requisition", requisition.getId(), null, requisition);
        return toDto(requisition);
    }

    // --- Hire bookkeeping (called by NewHireHandoffService) -------------------

    /**
     * Records a completed hire against the requisition: increments the filled count and
     * auto-flips the requisition to FILLED when the headcount is reached.
     */
    @Transactional
    public void recordHire(UUID requisitionId, UUID actorId) {
        Requisition requisition = requisitionRepository.findByIdForUpdate(requisitionId)
            .orElseThrow(() -> new EntityNotFoundException("Requisition not found"));
        requisition.setFilledCount(requisition.getFilledCount() + 1);
        if (requisition.isFull() && requisition.getStatus().acceptsHires()) {
            requisition.setStatus(RequisitionStatus.FILLED);
            requisition.setClosedAt(Instant.now());
        }
        requisitionRepository.save(requisition);
        auditLogService.log(actorId, AuditAction.UPDATE, "recruitment_requisition", requisition.getId(), null, requisition);
    }

    // --- Workflow completion (dispatched by the approval engine) --------------

    @Transactional
    public void handleWorkflowCompletion(UUID requisitionId, WorkflowStatus status, UUID actorId) {
        Requisition requisition = requisitionRepository.findByIdForUpdate(requisitionId)
            .orElseThrow(() -> new EntityNotFoundException("Requisition not found"));

        if (requisition.getStatus() != RequisitionStatus.PENDING_APPROVAL) {
            log.info("Ignoring workflow completion for requisition {} in status {}", requisitionId, requisition.getStatus());
            return;
        }

        if (status == WorkflowStatus.APPROVED || status == WorkflowStatus.COMPLETED) {
            requisition.setStatus(RequisitionStatus.OPEN);
            requisition.setOpenedAt(Instant.now());
            requisitionRepository.save(requisition);
            auditLogService.log(actorId, AuditAction.APPROVE, "recruitment_requisition", requisition.getId(), null, requisition);
            notifyHiringManager(requisition, NotificationEventType.REQUISITION_APPROVED,
                "recruitment.requisition.approved.title", "recruitment.requisition.approved.body",
                "requisition.approved");
        } else if (status == WorkflowStatus.REJECTED) {
            requisition.setStatus(RequisitionStatus.DRAFT);
            requisitionRepository.save(requisition);
            auditLogService.log(actorId, AuditAction.REJECT, "recruitment_requisition", requisition.getId(), null, requisition);
            notifyHiringManager(requisition, NotificationEventType.REQUISITION_REJECTED,
                "recruitment.requisition.rejected.title", "recruitment.requisition.rejected.body",
                "requisition.rejected");
        }
    }

    // --- Helpers --------------------------------------------------------------

    Requisition load(UUID id) {
        return requisitionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Requisition not found"));
    }

    private void validateReferences(UUID jobTitleId, UUID departmentId, UUID hiringManagerEmployeeId, UUID payGradeId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new EntityNotFoundException("Department not found");
        }
        if (!employeeRepository.existsById(hiringManagerEmployeeId)) {
            throw new EntityNotFoundException("Hiring manager employee not found");
        }
        if (payGradeId != null && !payGradeRepository.existsById(payGradeId)) {
            throw new EntityNotFoundException("Pay grade not found");
        }
    }

    private void notifyApprovers(Requisition requisition) {
        approvalWorkflowRepository.findBySubjectTypeAndSubjectId(
                RequisitionApprovalWorkflowService.SUBJECT_TYPE, requisition.getId())
            .ifPresent(workflow -> {
                List<ApprovalStep> steps = approvalStepRepository.findByWorkflowIdOrderByStepOrder(workflow.getId());
                for (ApprovalStep step : steps) {
                    User approver = userRepository.findById(step.getApproverId()).orElse(null);
                    if (approver == null) {
                        continue;
                    }
                    Map<String, Object> params = baseParams(requisition);
                    params.put("linkPath", "/approvals");
                    notificationPublisher.publishAfterCommit(NotificationEvent.builder()
                        .eventType(NotificationEventType.REQUISITION_SUBMITTED)
                        .targetUserId(approver.getId())
                        .titleKey("recruitment.requisition.submitted.title")
                        .bodyKey("recruitment.requisition.submitted.body")
                        .params(serializeParams(params))
                        .locale(approver.getLocalePreference())
                        .routingKey("requisition.submitted")
                        .publishedAt(Instant.now())
                        .build());
                }
            });
    }

    private void notifyHiringManager(Requisition requisition, NotificationEventType type,
                                     String titleKey, String bodyKey, String routingKey) {
        Employee hiringManager = employeeRepository.findById(requisition.getHiringManagerEmployeeId()).orElse(null);
        if (hiringManager == null) {
            return;
        }
        User user = userRepository.findById(hiringManager.getUserId()).orElse(null);
        if (user == null) {
            return;
        }
        Map<String, Object> params = baseParams(requisition);
        params.put("linkPath", "/recruitment/" + requisition.getId());
        notificationPublisher.publishAfterCommit(NotificationEvent.builder()
            .eventType(type)
            .targetUserId(user.getId())
            .titleKey(titleKey)
            .bodyKey(bodyKey)
            .params(serializeParams(params))
            .locale(user.getLocalePreference())
            .routingKey(routingKey)
            .publishedAt(Instant.now())
            .build());
    }

    private Map<String, Object> baseParams(Requisition requisition) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", requisition.getTitle());
        params.put("requisitionId", requisition.getId().toString());
        return params;
    }

    private String serializeParams(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification params", e);
        }
    }

    private RequisitionDto toDto(Requisition r) {
        String departmentName = departmentRepository.findById(r.getDepartmentId())
            .map(Department::getName).orElse(null);
        String hiringManagerName = employeeRepository.findById(r.getHiringManagerEmployeeId())
            .flatMap(e -> userRepository.findById(e.getUserId()))
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse(null);
        String payGradeName = r.getPayGradeId() == null ? null
            : payGradeRepository.findById(r.getPayGradeId()).map(PayGrade::getName).orElse(null);
        long applicationCount = applicationRepository.countByRequisitionId(r.getId());

        return new RequisitionDto(
            r.getId(),
            r.getTitle(),
            r.getJobTitleId(),
            r.getDepartmentId(),
            departmentName,
            r.getHiringManagerEmployeeId(),
            hiringManagerName,
            r.getPayGradeId(),
            payGradeName,
            r.getEmploymentType(),
            r.getLocation(),
            r.getHeadcount(),
            r.getFilledCount(),
            r.getDescription(),
            r.getStatus(),
            r.getOpenedAt(),
            r.getClosedAt(),
            r.getCreatedAt(),
            applicationCount
        );
    }
}
