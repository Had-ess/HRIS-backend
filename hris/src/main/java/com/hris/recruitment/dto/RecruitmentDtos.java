package com.hris.recruitment.dto;

import com.hris.auth.enums.ContractType;
import com.hris.recruitment.enums.ApplicationStage;
import com.hris.recruitment.enums.CandidateSource;
import com.hris.recruitment.enums.NewHireStatus;
import com.hris.recruitment.enums.RequisitionStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request/response DTOs for the recruitment module (Phase 1).
 */
public final class RecruitmentDtos {

    private RecruitmentDtos() {}

    // --- Requisitions ---------------------------------------------------------

    public record RequisitionCreateDto(
        @NotBlank @Size(max = 255) String title,
        @NotNull UUID jobTitleId,
        @NotNull UUID departmentId,
        @NotNull UUID hiringManagerEmployeeId,
        UUID payGradeId,
        @NotNull ContractType employmentType,
        @Size(max = 100) String location,
        @Min(1) int headcount,
        String description
    ) {}

    public record RequisitionUpdateDto(
        @NotBlank @Size(max = 255) String title,
        @NotNull UUID jobTitleId,
        @NotNull UUID departmentId,
        @NotNull UUID hiringManagerEmployeeId,
        UUID payGradeId,
        @NotNull ContractType employmentType,
        @Size(max = 100) String location,
        @Min(1) int headcount,
        String description
    ) {}

    public record RequisitionDto(
        UUID id,
        String title,
        UUID jobTitleId,
        UUID departmentId,
        String departmentName,
        UUID hiringManagerEmployeeId,
        String hiringManagerName,
        UUID payGradeId,
        String payGradeName,
        ContractType employmentType,
        String location,
        int headcount,
        int filledCount,
        String description,
        RequisitionStatus status,
        Instant openedAt,
        Instant closedAt,
        Instant createdAt,
        long applicationCount
    ) {}

    // --- Candidates -----------------------------------------------------------

    public record CandidateCreateDto(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @NotNull CandidateSource source,
        @Size(max = 255) String currentTitle,
        @Size(max = 255) String currentCompany,
        @Size(max = 100) String location,
        @Size(max = 1000) String resumeUrl,
        String notes
    ) {}

    public record CandidateUpdateDto(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @NotNull CandidateSource source,
        @Size(max = 255) String currentTitle,
        @Size(max = 255) String currentCompany,
        @Size(max = 100) String location,
        @Size(max = 1000) String resumeUrl,
        String notes
    ) {}

    public record CandidateDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        CandidateSource source,
        String currentTitle,
        String currentCompany,
        String location,
        String resumeUrl,
        String notes,
        Instant createdAt
    ) {}

    // --- Applications ---------------------------------------------------------

    public record ApplicationCreateDto(
        @NotNull UUID requisitionId,
        @NotNull UUID candidateId,
        CandidateSource source
    ) {}

    public record ApplicationMoveDto(
        @NotNull ApplicationStage stage,
        @Size(max = 500) String note
    ) {}

    public record ApplicationRatingDto(
        @NotNull @Min(1) @Max(5) Short rating
    ) {}

    public record ApplicationDto(
        UUID id,
        UUID requisitionId,
        UUID candidateId,
        CandidateDto candidate,
        ApplicationStage stage,
        Short rating,
        String rejectionReason,
        CandidateSource source,
        Instant appliedAt,
        Instant stageChangedAt,
        Instant hiredAt
    ) {}

    public record StageHistoryDto(
        UUID id,
        ApplicationStage fromStage,
        ApplicationStage toStage,
        String note,
        UUID changedById,
        Instant changedAt
    ) {}

    public record ApplicationDetailDto(
        ApplicationDto application,
        List<StageHistoryDto> history
    ) {}

    // --- New-hire handoff -----------------------------------------------------

    public record NewHireDto(
        UUID id,
        UUID applicationId,
        UUID candidateId,
        String candidateName,
        UUID requisitionId,
        String requisitionTitle,
        NewHireStatus status,
        LocalDate targetStartDate,
        UUID createdEmployeeId,
        Instant finalizedAt,
        Instant createdAt
    ) {}
}
