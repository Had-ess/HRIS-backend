package com.hris.auth.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.EmployeeDepartmentHistory;
import com.hris.auth.entity.EmployeeStatusHistory;
import com.hris.auth.repository.EmployeeDepartmentHistoryRepository;
import com.hris.auth.repository.EmployeeStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeHistoryService {

    private final EmployeeStatusHistoryRepository employeeStatusHistoryRepository;
    private final EmployeeDepartmentHistoryRepository employeeDepartmentHistoryRepository;

    public void recordHire(Employee employee, UUID actorId) {
        Instant recordedAt = Instant.now();

        employeeStatusHistoryRepository.save(EmployeeStatusHistory.builder()
            .employeeId(employee.getId())
            .previousStatus(null)
            .newStatus(employee.getStatus())
            .effectiveDate(employee.getHireDate())
            .reason("ONBOARDING")
            .changedBy(actorId)
            .recordedAt(recordedAt)
            .build());

        employeeDepartmentHistoryRepository.save(EmployeeDepartmentHistory.builder()
            .employeeId(employee.getId())
            .previousDepartmentId(null)
            .newDepartmentId(employee.getDepartmentId())
            .effectiveDate(employee.getHireDate())
            .changedBy(actorId)
            .recordedAt(recordedAt)
            .build());
    }

    public void recordDepartmentTransfer(Employee previous, Employee current, UUID actorId, LocalDate effectiveDate) {
        if (previous.getDepartmentId() == null || previous.getDepartmentId().equals(current.getDepartmentId())) {
            return;
        }

        employeeDepartmentHistoryRepository.save(EmployeeDepartmentHistory.builder()
            .employeeId(current.getId())
            .previousDepartmentId(previous.getDepartmentId())
            .newDepartmentId(current.getDepartmentId())
            .effectiveDate(effectiveDate)
            .changedBy(actorId)
            .recordedAt(Instant.now())
            .build());
    }

    public void recordStatusChange(Employee previous, Employee current, UUID actorId, LocalDate effectiveDate, String reason) {
        if (previous.getStatus() == current.getStatus()) {
            return;
        }

        employeeStatusHistoryRepository.save(EmployeeStatusHistory.builder()
            .employeeId(current.getId())
            .previousStatus(previous.getStatus())
            .newStatus(current.getStatus())
            .effectiveDate(effectiveDate)
            .reason(reason)
            .changedBy(actorId)
            .recordedAt(Instant.now())
            .build());
    }
}
