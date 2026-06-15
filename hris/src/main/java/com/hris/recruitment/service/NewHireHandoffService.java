package com.hris.recruitment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.recruitment.dto.RecruitmentDtos.NewHireDto;
import com.hris.recruitment.entity.Application;
import com.hris.recruitment.entity.Candidate;
import com.hris.recruitment.entity.NewHire;
import com.hris.recruitment.entity.Requisition;
import com.hris.recruitment.enums.NewHireStatus;
import com.hris.recruitment.repository.CandidateRepository;
import com.hris.recruitment.repository.NewHireRepository;
import com.hris.recruitment.repository.RequisitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the handoff bridge between a HIRED application and the authoritative employee-creation
 * flow. A handoff is created on HIRED (notifying HR); HR finalizes it through the existing
 * onboarding flow, which links the created employee and increments the requisition headcount.
 */
@Service
@RequiredArgsConstructor
public class NewHireHandoffService {

    private static final String MANAGE_PERMISSION = "RECRUITMENT_MANAGE";

    private final NewHireRepository newHireRepository;
    private final RequisitionRepository requisitionRepository;
    private final CandidateRepository candidateRepository;
    private final RequisitionService requisitionService;
    private final UserRepository userRepository;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Creates the PENDING handoff for a freshly-HIRED application. Called from
     * {@link ApplicationService} inside the same transaction as the stage move.
     */
    @Transactional
    public NewHire createForHiredApplication(Application application, UUID actorId) {
        if (newHireRepository.findByApplicationId(application.getId()).isPresent()) {
            throw new InvalidWorkflowStateException("A new-hire handoff already exists for this application");
        }
        NewHire newHire = newHireRepository.save(NewHire.builder()
            .applicationId(application.getId())
            .candidateId(application.getCandidateId())
            .requisitionId(application.getRequisitionId())
            .status(NewHireStatus.PENDING)
            .createdById(actorId)
            .build());
        auditLogService.log(actorId, AuditAction.CREATE, "recruitment_new_hire", newHire.getId(), null, newHire);
        notifyHr(newHire);
        return newHire;
    }

    @Transactional(readOnly = true)
    public NewHireDto get(UUID id) {
        return toDto(newHireRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("New-hire handoff not found")));
    }

    @Transactional(readOnly = true)
    public List<NewHireDto> list(NewHireStatus status) {
        List<NewHire> handoffs = status == null
            ? newHireRepository.findAllByOrderByCreatedAtDesc()
            : newHireRepository.findByStatusOrderByCreatedAtDesc(status);
        return handoffs.stream().map(this::toDto).toList();
    }

    /**
     * Marks the handoff COMPLETED, links the created employee, and records the hire against
     * the requisition (filled-count increment + auto-FILLED). Driven by the employee-creation
     * flow once HR finalizes the new employee.
     */
    @Transactional
    public NewHireDto complete(UUID handoffId, UUID createdEmployeeId, UUID actorId) {
        NewHire newHire = newHireRepository.findByIdForUpdate(handoffId)
            .orElseThrow(() -> new EntityNotFoundException("New-hire handoff not found"));
        if (newHire.getStatus() != NewHireStatus.PENDING) {
            throw new InvalidWorkflowStateException("Only PENDING handoffs can be completed");
        }
        newHire.setStatus(NewHireStatus.COMPLETED);
        newHire.setCreatedEmployeeId(createdEmployeeId);
        newHire.setFinalizedAt(Instant.now());
        newHire.setFinalizedById(actorId);
        newHireRepository.save(newHire);
        auditLogService.log(actorId, AuditAction.COMPLETE, "recruitment_new_hire", newHire.getId(), null, newHire);

        requisitionService.recordHire(newHire.getRequisitionId(), actorId);
        return toDto(newHire);
    }

    @Transactional
    public NewHireDto cancel(UUID handoffId, UUID actorId) {
        NewHire newHire = newHireRepository.findByIdForUpdate(handoffId)
            .orElseThrow(() -> new EntityNotFoundException("New-hire handoff not found"));
        if (newHire.getStatus() != NewHireStatus.PENDING) {
            throw new InvalidWorkflowStateException("Only PENDING handoffs can be cancelled");
        }
        newHire.setStatus(NewHireStatus.CANCELLED);
        newHire.setFinalizedAt(Instant.now());
        newHire.setFinalizedById(actorId);
        newHireRepository.save(newHire);
        auditLogService.log(actorId, AuditAction.CANCEL, "recruitment_new_hire", newHire.getId(), null, newHire);
        return toDto(newHire);
    }

    private void notifyHr(NewHire newHire) {
        String candidateName = candidateRepository.findById(newHire.getCandidateId())
            .map(c -> c.getFirstName() + " " + c.getLastName()).orElse("");
        String requisitionTitle = requisitionRepository.findById(newHire.getRequisitionId())
            .map(Requisition::getTitle).orElse("");
        for (User user : userRepository.findByPermissionNames(List.of(MANAGE_PERMISSION))) {
            if (user == null || !user.isActive()) {
                continue;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("candidateName", candidateName);
            params.put("title", requisitionTitle);
            params.put("linkPath", "/recruitment/new-hires");
            notificationPublisher.publishAfterCommit(NotificationEvent.builder()
                .eventType(NotificationEventType.NEW_HIRE_HANDOFF)
                .targetUserId(user.getId())
                .titleKey("recruitment.newHire.handoff.title")
                .bodyKey("recruitment.newHire.handoff.body")
                .params(serializeParams(params))
                .locale(user.getLocalePreference())
                .routingKey("recruitment.newHire.handoff")
                .publishedAt(Instant.now())
                .build());
        }
    }

    private String serializeParams(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification params", e);
        }
    }

    private NewHireDto toDto(NewHire n) {
        String candidateName = candidateRepository.findById(n.getCandidateId())
            .map(c -> c.getFirstName() + " " + c.getLastName()).orElse(null);
        String requisitionTitle = requisitionRepository.findById(n.getRequisitionId())
            .map(Requisition::getTitle).orElse(null);
        return new NewHireDto(
            n.getId(), n.getApplicationId(), n.getCandidateId(), candidateName,
            n.getRequisitionId(), requisitionTitle, n.getStatus(), n.getTargetStartDate(),
            n.getCreatedEmployeeId(), n.getFinalizedAt(), n.getCreatedAt());
    }
}
