package com.hris.lifecycle.dto;

import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.lifecycle.enums.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response records of the lifecycle API (see EMPLOYEE_LIFECYCLE_DESIGN.md §7). */
public final class LifecycleDtos {

    private LifecycleDtos() {
    }

    public record CreateContractRequest(
        @NotNull ContractType contractType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        LocalDate probationEndDate,
        @Size(max = 500) String note
    ) {
    }

    public record ContractDto(
        UUID id,
        UUID employeeId,
        ContractType contractType,
        ContractStatus status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate probationEndDate,
        String note,
        Instant createdAt
    ) {
    }

    public record TerminateRequest(
        @NotNull LocalDate terminationDate,
        @NotBlank @Size(max = 255) String reason
    ) {
    }

    public record ReactivateRequest(
        @NotBlank @Size(max = 255) String reason
    ) {
    }

    /** Department and/or supervisor change; at least one target must be set. */
    public record TransferRequest(
        @NotNull LocalDate effectiveDate,
        UUID departmentId,
        UUID supervisorEmployeeId
    ) {
    }

    public record ScheduledTransferDto(
        LocalDate effectiveDate,
        UUID departmentId,
        String departmentName,
        UUID supervisorEmployeeId,
        String supervisorName
    ) {
    }

    /**
     * One entry of the merged lifecycle timeline. kind = STATUS | TRANSFER | CONTRACT.
     * Field meaning depends on kind; unused fields are null.
     */
    public record LifecycleEventDto(
        String kind,
        LocalDate effectiveDate,
        Instant recordedAt,
        EmployeeStatus previousStatus,
        EmployeeStatus newStatus,
        String previousDepartmentName,
        String newDepartmentName,
        ContractType contractType,
        ContractStatus contractStatus,
        LocalDate contractEndDate,
        String detail
    ) {
    }

    public record LifecycleStateDto(
        EmployeeStatus status,
        LocalDate terminationDate,
        boolean terminationScheduled,
        ScheduledTransferDto scheduledTransfer,
        ContractDto activeContract,
        List<LifecycleEventDto> timeline
    ) {
    }
}
