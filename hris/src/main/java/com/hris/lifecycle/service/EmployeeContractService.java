package com.hris.lifecycle.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.ContractType;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.lifecycle.dto.LifecycleDtos.ContractDto;
import com.hris.lifecycle.dto.LifecycleDtos.CreateContractRequest;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeContractService {

    private final EmployeeContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;

    /** Scope is enforced by EmployeeService.getById (department/self rules). */
    @Transactional(readOnly = true)
    public List<ContractDto> listContracts(UUID employeeId, UUID requesterId) {
        employeeService.getById(employeeId, requesterId);
        return contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId)
            .stream().map(EmployeeContractService::toDto).toList();
    }

    /**
     * Creates a new contract; any existing ACTIVE contract is superseded and,
     * when open-ended or overlapping, closed the day before the new start.
     * employees.contract_type follows the active contract (leave policies match on it).
     */
    @Transactional
    public ContractDto createContract(UUID employeeId, CreateContractRequest request, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        validate(request);

        contractRepository.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE)
            .ifPresent(current -> supersede(current, request.startDate()));

        EmployeeContract created = contractRepository.save(EmployeeContract.builder()
            .employeeId(employeeId)
            .contractType(request.contractType())
            .status(ContractStatus.ACTIVE)
            .startDate(request.startDate())
            .endDate(request.endDate())
            .probationEndDate(request.probationEndDate())
            .note(trimmedOrNull(request.note()))
            .build());

        if (employee.getContractType() != request.contractType()) {
            employee.setContractType(request.contractType());
            employeeRepository.save(employee);
        }

        auditLogService.log(actorId, AuditAction.CREATE, "employee_contract",
            created.getId(), null, created);
        return toDto(created);
    }

    private void supersede(EmployeeContract current, LocalDate newStart) {
        current.setStatus(ContractStatus.SUPERSEDED);
        LocalDate closedEnd = newStart.minusDays(1);
        if (current.getEndDate() == null || current.getEndDate().isAfter(closedEnd)) {
            // never end a contract before it started (degenerate same-day replacement)
            current.setEndDate(closedEnd.isBefore(current.getStartDate())
                ? current.getStartDate()
                : closedEnd);
        }
        // Flush the UPDATE before the new ACTIVE row is inserted: Hibernate orders
        // INSERTs before UPDATEs within a flush, which would trip the one-ACTIVE
        // partial unique index (uq_employee_contracts_active).
        contractRepository.saveAndFlush(current);
    }

    private void validate(CreateContractRequest request) {
        boolean endRequired = request.contractType() == ContractType.FIXED_TERM
            || request.contractType() == ContractType.INTERNSHIP;
        if (endRequired && request.endDate() == null) {
            throw new IllegalArgumentException("An end date is required for fixed-term and internship contracts");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Contract end date cannot be before its start date");
        }
        if (request.probationEndDate() != null && request.probationEndDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Probation end date cannot be before the contract start date");
        }
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static ContractDto toDto(EmployeeContract contract) {
        return new ContractDto(
            contract.getId(),
            contract.getEmployeeId(),
            contract.getContractType(),
            contract.getStatus(),
            contract.getStartDate(),
            contract.getEndDate(),
            contract.getProbationEndDate(),
            contract.getNote(),
            contract.getCreatedAt()
        );
    }
}
