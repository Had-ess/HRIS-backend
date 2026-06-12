package com.hris.lifecycle.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.lifecycle.dto.LifecycleDtos.ContractDto;
import com.hris.lifecycle.dto.LifecycleDtos.CreateContractRequest;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeContractServiceTest {

    @Mock private EmployeeContractRepository contractRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeService employeeService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EmployeeContractService service;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
            .id(employeeId)
            .userId(UUID.randomUUID())
            .employeeCode("EMP-1")
            .hireDate(LocalDate.of(2024, 1, 1))
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .build();
        lenient().when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        lenient().when(contractRepository.save(any(EmployeeContract.class)))
            .thenAnswer(inv -> {
                EmployeeContract c = inv.getArgument(0);
                if (c.getId() == null) c.setId(UUID.randomUUID());
                return c;
            });
    }

    @Test
    void createFirstContractIsActiveAndSyncsEmployeeType() {
        when(contractRepository.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE))
            .thenReturn(Optional.empty());

        ContractDto dto = service.createContract(employeeId,
            new CreateContractRequest(ContractType.FIXED_TERM,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, " note "),
            actorId);

        assertThat(dto.status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(dto.note()).isEqualTo("note");
        assertThat(employee.getContractType()).isEqualTo(ContractType.FIXED_TERM);
        verify(employeeRepository).save(employee);
    }

    @Test
    void creatingNewContractSupersedesOpenEndedCurrentOne() {
        EmployeeContract current = EmployeeContract.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .contractType(ContractType.PERMANENT).status(ContractStatus.ACTIVE)
            .startDate(LocalDate.of(2024, 1, 1)).build();
        when(contractRepository.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE))
            .thenReturn(Optional.of(current));

        service.createContract(employeeId,
            new CreateContractRequest(ContractType.PERMANENT, LocalDate.of(2026, 6, 1), null, null, null),
            actorId);

        assertThat(current.getStatus()).isEqualTo(ContractStatus.SUPERSEDED);
        assertThat(current.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void supersedingNeverEndsContractBeforeItsStart() {
        EmployeeContract current = EmployeeContract.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .contractType(ContractType.PERMANENT).status(ContractStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 6, 1)).build();
        when(contractRepository.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE))
            .thenReturn(Optional.of(current));

        service.createContract(employeeId,
            new CreateContractRequest(ContractType.CONTRACTOR, LocalDate.of(2026, 6, 1), null, null, null),
            actorId);

        assertThat(current.getEndDate()).isEqualTo(current.getStartDate());
    }

    @Test
    void fixedTermRequiresEndDate() {
        assertThatThrownBy(() -> service.createContract(employeeId,
            new CreateContractRequest(ContractType.FIXED_TERM, LocalDate.of(2026, 1, 1), null, null, null),
            actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("end date");
    }

    @Test
    void endDateBeforeStartIsRejected() {
        assertThatThrownBy(() -> service.createContract(employeeId,
            new CreateContractRequest(ContractType.CONTRACTOR,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, null),
            actorId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listContractsEnforcesReadScopeThroughEmployeeService() {
        UUID requesterId = UUID.randomUUID();
        when(contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId))
            .thenReturn(java.util.List.of());

        service.listContracts(employeeId, requesterId);

        verify(employeeService).getById(employeeId, requesterId);
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(contractRepository).findByEmployeeIdOrderByStartDateDescCreatedAtDesc(captor.capture());
        assertThat(captor.getValue()).isEqualTo(employeeId);
    }
}
