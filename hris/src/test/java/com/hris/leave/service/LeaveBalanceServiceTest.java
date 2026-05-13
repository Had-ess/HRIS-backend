package com.hris.leave.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.dto.LeaveBalanceSummaryDto;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeavePolicyRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
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
class LeaveBalanceServiceTest {

    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeavePolicyRepository leavePolicyRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private AccessScopeService accessScopeService;
    @Mock private LeaveBalanceLedgerService leaveBalanceLedgerService;
    @Mock private LeaveAcquisitionPolicyRepository leaveAcquisitionPolicyRepository;

    @InjectMocks
    private LeaveBalanceService leaveBalanceService;

    @Test
    @DisplayName("getVisibleBalances returns an empty list when no balances exist")
    void getVisibleBalancesReturnsEmptyList() {
        UUID requesterId = UUID.randomUUID();
        when(accessScopeService.hasAnyPermissionName(requesterId,
            "LEAVE_BALANCE_READ_OWN", "LEAVE_BALANCE_READ_SCOPED", "LEAVE_BALANCE_MANAGE")).thenReturn(true);
        when(accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_MANAGE")).thenReturn(true);
        when(leaveBalanceRepository.searchSummariesForYear(any(Integer.class), eq(null), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        List<LeaveBalanceSummaryDto> result = leaveBalanceService.getVisibleBalances(
            requesterId, null, null, 2026, PageRequest.of(0, 20));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getVisibleBalances scopes department readers to visible employees")
    void getVisibleBalancesScopesDepartmentReaders() {
        UUID requesterId = UUID.randomUUID();
        UUID requesterEmployeeId = UUID.randomUUID();
        UUID employeeOneId = UUID.randomUUID();
        UUID employeeTwoId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Employee requesterEmployee = Employee.builder().id(requesterEmployeeId).departmentId(departmentId).build();
        LeaveBalanceSummaryDto summary = new LeaveBalanceSummaryDto(
            UUID.randomUUID(), employeeOneId, "E-01", UUID.randomUUID(), "Alice", "Doe",
            UUID.randomUUID(), "ANNUAL", "Annual Leave", 2026, 20, 3, 1, 0, 16);

        when(accessScopeService.hasAnyPermissionName(requesterId,
            "LEAVE_BALANCE_READ_OWN", "LEAVE_BALANCE_READ_SCOPED", "LEAVE_BALANCE_MANAGE")).thenReturn(true);
        when(accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_MANAGE")).thenReturn(false);
        when(accessScopeService.hasGlobalBusinessRead(requesterId)).thenReturn(false);
        when(accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_READ_SCOPED")).thenReturn(true);
        when(accessScopeService.getEmployeeOrThrow(requesterId)).thenReturn(requesterEmployee);
        when(accessScopeService.resolveDepartmentManagerDepartmentId(requesterId, requesterEmployee))
            .thenReturn(Optional.of(departmentId));
        when(employeeRepository.findByDepartmentId(departmentId)).thenReturn(List.of(
            Employee.builder().id(employeeOneId).departmentId(departmentId).build(),
            Employee.builder().id(employeeTwoId).departmentId(departmentId).build()
        ));
        when(leaveBalanceRepository.searchSummariesForYearAndEmployeeIds(
            eq(2026), eq(List.of(employeeOneId, employeeTwoId)), eq("alice"), any()))
            .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        List<LeaveBalanceSummaryDto> result = leaveBalanceService.getVisibleBalances(
            requesterId, null, "alice", 2026, PageRequest.of(0, 20));

        assertThat(result).containsExactly(summary);
        verify(leaveBalanceRepository, never()).searchSummariesForYear(eq(2026), eq(null), eq("alice"), any());
    }

    @Test
    @DisplayName("getVisibleBalances limits own readers to their own employee balance")
    void getVisibleBalancesLimitsOwnReaders() {
        UUID requesterId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee requesterEmployee = Employee.builder().id(employeeId).build();
        LeaveBalanceSummaryDto summary = new LeaveBalanceSummaryDto(
            UUID.randomUUID(), employeeId, "E-01", requesterId, "Alice", "Doe",
            UUID.randomUUID(), "ANNUAL", "Annual Leave", 2026, 20, 3, 1, 0, 16);

        when(accessScopeService.hasAnyPermissionName(requesterId,
            "LEAVE_BALANCE_READ_OWN", "LEAVE_BALANCE_READ_SCOPED", "LEAVE_BALANCE_MANAGE")).thenReturn(true);
        when(accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_MANAGE")).thenReturn(false);
        when(accessScopeService.hasGlobalBusinessRead(requesterId)).thenReturn(false);
        when(accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_READ_SCOPED")).thenReturn(false);
        when(accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_READ_OWN")).thenReturn(true);
        when(accessScopeService.getEmployeeOrThrow(requesterId)).thenReturn(requesterEmployee);
        when(leaveBalanceRepository.searchSummariesForYearAndEmployeeIds(
            eq(2026), eq(List.of(employeeId)), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        List<LeaveBalanceSummaryDto> result = leaveBalanceService.getVisibleBalances(
            requesterId, null, null, 2026, PageRequest.of(0, 20));

        assertThat(result).containsExactly(summary);
    }

    @Test
    @DisplayName("getVisibleBalances rejects invalid years")
    void getVisibleBalancesRejectsInvalidYear() {
        assertThatThrownBy(() -> leaveBalanceService.getVisibleBalances(
            UUID.randomUUID(), null, null, 1900, PageRequest.of(0, 20)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("year must be between 2000 and 3000");
    }

    @Test
    @DisplayName("getVisibleBalances denies access without qualifying permissions")
    void getVisibleBalancesDeniesWithoutPermission() {
        UUID requesterId = UUID.randomUUID();
        when(accessScopeService.hasAnyPermissionName(requesterId,
            "LEAVE_BALANCE_READ_OWN", "LEAVE_BALANCE_READ_SCOPED", "LEAVE_BALANCE_MANAGE"))
            .thenReturn(false);

        assertThatThrownBy(() -> leaveBalanceService.getVisibleBalances(
            requesterId, null, null, 2026, PageRequest.of(0, 20)))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("You are not allowed to browse leave balances");
    }
}
