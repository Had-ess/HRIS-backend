package com.hris.recruitment.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationDetailDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationMoveDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationRatingDto;
import com.hris.recruitment.dto.RecruitmentDtos.StageHistoryDto;
import com.hris.recruitment.entity.Application;
import com.hris.recruitment.entity.ApplicationStageHistory;
import com.hris.recruitment.entity.Candidate;
import com.hris.recruitment.entity.Requisition;
import com.hris.recruitment.enums.ApplicationStage;
import com.hris.recruitment.enums.RequisitionStatus;
import com.hris.recruitment.repository.ApplicationRepository;
import com.hris.recruitment.repository.ApplicationStageHistoryRepository;
import com.hris.recruitment.repository.CandidateRepository;
import com.hris.recruitment.repository.RequisitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStageHistoryRepository stageHistoryRepository;
    private final RequisitionRepository requisitionRepository;
    private final CandidateRepository candidateRepository;
    private final NewHireHandoffService newHireHandoffService;
    private final CandidateService candidateService;
    private final AuditLogService auditLogService;

    // --- Queries --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApplicationDto> listByRequisition(UUID requisitionId) {
        return applicationRepository.findByRequisitionIdOrderByAppliedAtAsc(requisitionId)
            .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> listByCandidate(UUID candidateId) {
        return applicationRepository.findByCandidateIdOrderByAppliedAtDesc(candidateId)
            .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDetailDto getDetail(UUID id) {
        Application application = load(id);
        List<StageHistoryDto> history = stageHistoryRepository
            .findByApplicationIdOrderByChangedAtAsc(id)
            .stream().map(this::toHistoryDto).toList();
        return new ApplicationDetailDto(toDto(application), history);
    }

    // --- Mutations ------------------------------------------------------------

    @Transactional
    public ApplicationDto create(ApplicationCreateDto dto, UUID userId) {
        Requisition requisition = requisitionRepository.findById(dto.requisitionId())
            .orElseThrow(() -> new EntityNotFoundException("Requisition not found"));
        if (requisition.getStatus() != RequisitionStatus.OPEN) {
            throw new InvalidWorkflowStateException("Candidates can only be added to an OPEN requisition");
        }
        Candidate candidate = candidateRepository.findById(dto.candidateId())
            .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
        if (applicationRepository.existsByRequisitionIdAndCandidateId(dto.requisitionId(), dto.candidateId())) {
            throw new IllegalStateException("This candidate has already been added to this requisition");
        }

        Application application = applicationRepository.save(Application.builder()
            .requisitionId(dto.requisitionId())
            .candidateId(dto.candidateId())
            .stage(ApplicationStage.APPLIED)
            .source(dto.source() != null ? dto.source() : candidate.getSource())
            .appliedAt(Instant.now())
            .stageChangedAt(Instant.now())
            .createdById(userId)
            .build());

        writeHistory(application.getId(), null, ApplicationStage.APPLIED, null, userId);
        auditLogService.log(userId, AuditAction.CREATE, "recruitment_application", application.getId(), null, application);
        return toDto(application);
    }

    @Transactional
    public ApplicationDto moveStage(UUID id, ApplicationMoveDto dto, UUID userId) {
        Application application = applicationRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new EntityNotFoundException("Application not found"));
        ApplicationStage from = application.getStage();
        ApplicationStage to = dto.stage();
        validateTransition(from, to);

        if (to == ApplicationStage.HIRED) {
            Requisition requisition = requisitionRepository.findById(application.getRequisitionId())
                .orElseThrow(() -> new EntityNotFoundException("Requisition not found"));
            if (!requisition.getStatus().acceptsHires()) {
                throw new InvalidWorkflowStateException("Requisition is not open for hiring");
            }
            if (requisition.isFull()) {
                throw new InvalidWorkflowStateException("Requisition headcount is already filled");
            }
            application.setHiredAt(Instant.now());
        }

        if (to == ApplicationStage.REJECTED || to == ApplicationStage.WITHDRAWN) {
            application.setRejectionReason(dto.note());
        }

        application.setStage(to);
        application.setStageChangedAt(Instant.now());
        applicationRepository.save(application);
        writeHistory(application.getId(), from, to, dto.note(), userId);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_application", application.getId(), null, application);

        if (to == ApplicationStage.HIRED) {
            newHireHandoffService.createForHiredApplication(application, userId);
        }
        return toDto(application);
    }

    @Transactional
    public ApplicationDto setRating(UUID id, ApplicationRatingDto dto, UUID userId) {
        Application application = load(id);
        application.setRating(dto.rating());
        applicationRepository.save(application);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_application", application.getId(), null, application);
        return toDto(application);
    }

    // --- Helpers --------------------------------------------------------------

    private void validateTransition(ApplicationStage from, ApplicationStage to) {
        if (from.isTerminal()) {
            throw new InvalidWorkflowStateException(
                "Application is in a terminal stage (" + from + ") and cannot be moved");
        }
        if (from == to) {
            throw new InvalidWorkflowStateException("Application is already in stage " + to);
        }
        // From any non-terminal stage, moving to any other stage is allowed: recruiters may
        // progress, step back, or terminate (HIRED/REJECTED/WITHDRAWN).
    }

    private void writeHistory(UUID applicationId, ApplicationStage from, ApplicationStage to, String note, UUID userId) {
        stageHistoryRepository.save(ApplicationStageHistory.builder()
            .applicationId(applicationId)
            .fromStage(from)
            .toStage(to)
            .note(note)
            .changedById(userId)
            .changedAt(Instant.now())
            .build());
    }

    Application load(UUID id) {
        return applicationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Application not found"));
    }

    private ApplicationDto toDto(Application a) {
        var candidate = candidateRepository.findById(a.getCandidateId())
            .map(candidateService::toDto).orElse(null);
        return new ApplicationDto(
            a.getId(), a.getRequisitionId(), a.getCandidateId(), candidate,
            a.getStage(), a.getRating(), a.getRejectionReason(), a.getSource(),
            a.getAppliedAt(), a.getStageChangedAt(), a.getHiredAt());
    }

    private StageHistoryDto toHistoryDto(ApplicationStageHistory h) {
        return new StageHistoryDto(h.getId(), h.getFromStage(), h.getToStage(),
            h.getNote(), h.getChangedById(), h.getChangedAt());
    }
}
