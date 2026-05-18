package com.hris.auth.dto;

import com.hris.auth.enums.AccountStatus;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.leave.dto.LeaveBalanceDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Compact profile summary for the Employee Profile hero banner.
 * Returned by GET /api/employees/{id}/profile-summary.
 * Combines core identity fields + live leave balances + org context
 * so the UI can render a rich hero card without waterfall requests.
 */
public record EmployeeProfileSummaryDto(
    UUID id,
    UUID userId,
    String employeeCode,
    String firstName,
    String lastName,
    String email,
    String jobTitle,
    String departmentName,
    String supervisorName,
    LocalDate hireDate,
    EmployeeStatus status,
    AccountStatus accountStatus,
    ContractType contractType,
    String location,
    /** Years of service, rounded to 1 decimal */
    double yearsOfService,
    /** Current-year leave balances (top 6) */
    List<LeaveBalanceDto> leaveBalances,
    /** Count of direct reports (0 if not a manager) */
    int directReportCount
) {}
