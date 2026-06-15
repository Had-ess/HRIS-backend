package com.hris.recruitment.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationMoveDto;
import com.hris.recruitment.entity.Application;
import com.hris.recruitment.entity.ApplicationStageHistory;
import com.hris.recruitment.entity.Candidate;
import com.hris.recruitment.entity.Requisition;
import com.hris.recruitment.enums.ApplicationStage;
import com.hris.recruitment.enums.CandidateSource;
import com.hris.recruitment.enums.RequisitionStatus;
import com.hris.recruitment.repository.ApplicationRepository;
import com.hris.recruitment.repository.ApplicationStageHistoryRepository;
import com.hris.recruitment.repository.CandidateRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationStageHistoryRepository stageHistoryRepository;
    @Mock private RequisitionRepository requisitionRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private NewHireHandoffService newHireHandoffService;
    @Mock private CandidateService candidateService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ApplicationService service;

    private final UUID userId = UUID.randomUUID();

    private Requisition reqWithStatus(RequisitionStatus status, int headcount, int filled) {
        return Requisition.builder()
            .id(UUID.randomUUID()).title("Role").status(status)
            .headcount(headcount).filledCount(filled).build();
    }

    private Candidate candidate() {
        return Candidate.builder().id(UUID.randomUUID()).firstName("A").lastName("B")
            .email("a@b.co").source(CandidateSource.DIRECT).build();
    }

    private Application appAt(ApplicationStage stage) {
        return Application.builder()
            .id(UUID.randomUUID()).requisitionId(UUID.randomUUID()).candidateId(UUID.randomUUID())
            .stage(stage).source(CandidateSource.DIRECT).build();
    }

    @Test
    void create_requiresOpenRequisition() {
        Requisition req = reqWithStatus(RequisitionStatus.DRAFT, 1, 0);
        Candidate c = candidate();
        when(requisitionRepository.findById(req.getId())).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.create(new ApplicationCreateDto(req.getId(), c.getId(), null), userId))
            .isInstanceOf(InvalidWorkflowStateException.class);
    }

    @Test
    void create_rejectsDuplicateCandidate() {
        Requisition req = reqWithStatus(RequisitionStatus.OPEN, 1, 0);
        Candidate c = candidate();
        when(requisitionRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(candidateRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(applicationRepository.existsByRequisitionIdAndCandidateId(req.getId(), c.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ApplicationCreateDto(req.getId(), c.getId(), null), userId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void create_writesAppliedHistory() {
        Requisition req = reqWithStatus(RequisitionStatus.OPEN, 1, 0);
        Candidate c = candidate();
        when(requisitionRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(candidateRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(applicationRepository.existsByRequisitionIdAndCandidateId(any(), any())).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> {
            Application a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        service.create(new ApplicationCreateDto(req.getId(), c.getId(), null), userId);

        verify(stageHistoryRepository).save(any(ApplicationStageHistory.class));
    }

    @Test
    void moveStage_rejectsMoveFromTerminal() {
        Application hired = appAt(ApplicationStage.HIRED);
        when(applicationRepository.findByIdForUpdate(hired.getId())).thenReturn(Optional.of(hired));

        assertThatThrownBy(() -> service.moveStage(hired.getId(),
            new ApplicationMoveDto(ApplicationStage.OFFER, null), userId))
            .isInstanceOf(InvalidWorkflowStateException.class);
    }

    @Test
    void moveStage_rejectsNoOp() {
        Application app = appAt(ApplicationStage.SCREENING);
        when(applicationRepository.findByIdForUpdate(app.getId())).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.moveStage(app.getId(),
            new ApplicationMoveDto(ApplicationStage.SCREENING, null), userId))
            .isInstanceOf(InvalidWorkflowStateException.class);
    }

    @Test
    void moveStage_progressesAndWritesHistory() {
        Application app = appAt(ApplicationStage.APPLIED);
        when(applicationRepository.findByIdForUpdate(app.getId())).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.moveStage(app.getId(), new ApplicationMoveDto(ApplicationStage.SCREENING, "looks good"), userId);

        assertThat(dto.stage()).isEqualTo(ApplicationStage.SCREENING);
        verify(stageHistoryRepository).save(any(ApplicationStageHistory.class));
        verify(newHireHandoffService, never()).createForHiredApplication(any(), any());
    }

    @Test
    void moveStage_rejectCapturesReason() {
        Application app = appAt(ApplicationStage.INTERVIEW);
        when(applicationRepository.findByIdForUpdate(app.getId())).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        service.moveStage(app.getId(), new ApplicationMoveDto(ApplicationStage.REJECTED, "not a fit"), userId);

        assertThat(app.getStage()).isEqualTo(ApplicationStage.REJECTED);
        assertThat(app.getRejectionReason()).isEqualTo("not a fit");
    }

    @Test
    void moveStage_hiredGuardedByCapacity() {
        Application app = appAt(ApplicationStage.OFFER);
        Requisition full = reqWithStatus(RequisitionStatus.OPEN, 1, 1);
        app.setRequisitionId(full.getId());
        when(applicationRepository.findByIdForUpdate(app.getId())).thenReturn(Optional.of(app));
        when(requisitionRepository.findById(full.getId())).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> service.moveStage(app.getId(),
            new ApplicationMoveDto(ApplicationStage.HIRED, null), userId))
            .isInstanceOf(InvalidWorkflowStateException.class);
        verify(newHireHandoffService, never()).createForHiredApplication(any(), any());
    }

    @Test
    void moveStage_hiredCreatesHandoff() {
        Application app = appAt(ApplicationStage.OFFER);
        Requisition open = reqWithStatus(RequisitionStatus.OPEN, 2, 0);
        app.setRequisitionId(open.getId());
        when(applicationRepository.findByIdForUpdate(app.getId())).thenReturn(Optional.of(app));
        when(requisitionRepository.findById(open.getId())).thenReturn(Optional.of(open));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        service.moveStage(app.getId(), new ApplicationMoveDto(ApplicationStage.HIRED, null), userId);

        assertThat(app.getStage()).isEqualTo(ApplicationStage.HIRED);
        assertThat(app.getHiredAt()).isNotNull();
        verify(newHireHandoffService).createForHiredApplication(any(), any());
    }
}
