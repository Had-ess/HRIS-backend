package com.hris.leave.repository;

import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.leave.dto.LeaveBalanceSummaryDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveType;
import com.hris.organisation.entity.WorkSchedule;
import com.hris.organisation.repository.WorkScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:leave-balance-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;INIT=CREATE SCHEMA IF NOT EXISTS public",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeaveBalanceRepositoryTest {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Test
    @DisplayName("searchSummariesForYear returns computed available days")
    void searchSummariesForYearReturnsComputedAvailableDays() {
        User user = userRepository.save(User.builder()
            .keycloakId("kc-test-user")
            .email("alice.doe@demo.hris.local")
            .firstName("Alice")
            .lastName("Doe")
            .build());

        Department department = departmentRepository.save(Department.builder()
            .name("Engineering")
            .code("ENG-TDD")
            .build());

        WorkSchedule workSchedule = workScheduleRepository.save(WorkSchedule.builder()
            .name("Default")
            .workingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
            .hoursPerDay(8)
            .build());

        Employee employee = employeeRepository.save(Employee.builder()
            .userId(user.getId())
            .employeeCode("EMP-TDD-001")
            .hireDate(LocalDate.of(2024, 1, 1))
            .jobTitle("Engineer")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(department.getId())
            .workScheduleId(workSchedule.getId())
            .build());

        LeaveType leaveType = leaveTypeRepository.save(LeaveType.builder()
            .code("ANNUAL-TDD")
            .name("Annual Leave")
            .build());

        leaveBalanceRepository.save(LeaveBalance.builder()
            .employeeId(employee.getId())
            .leaveTypeId(leaveType.getId())
            .year(2026)
            .totalDays(20)
            .usedDays(3)
            .pendingDays(2)
            .carryOverDays(1)
            .build());

        Page<LeaveBalanceSummaryDto> result = leaveBalanceRepository.searchSummariesForYearWithQuery(
            2026,
            null,
            "alice",
            PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).availableDays()).isEqualTo(16);
        assertThat(result.getContent().get(0).employeeId()).isEqualTo(employee.getId());
        assertThat(result.getContent().get(0).leaveTypeId()).isEqualTo(leaveType.getId());
    }
}
