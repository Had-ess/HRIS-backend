package com.hris.access.service;

import com.hris.access.enums.StructuralEventType;
import com.hris.access.event.StructuralChangeEvent;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Replays the current structural facts of the database as
 * {@link StructuralChangeEvent}s so the automatic profile assignment engine
 * can reconcile profile grants on a fresh deployment.
 *
 * <p>Idempotent by construction: the engine's grant() skips when an active
 * assignment already exists, so a second run is a no-op. To avoid the
 * scanning cost on every restart, this runner short-circuits once at least
 * one SYSTEM-sourced assignment already exists.
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class ProfileBootstrapMigration implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JdbcTemplate jdbcTemplate;

    @Value("${hris.profile-assignment.bootstrap-on-startup:true}")
    private boolean bootstrapEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!bootstrapEnabled) {
            log.info("ProfileBootstrapMigration disabled via hris.profile-assignment.bootstrap-on-startup=false");
            return;
        }
        if (alreadyBootstrapped()) {
            log.info("ProfileBootstrapMigration: SYSTEM-sourced assignments already exist — skipping replay");
            return;
        }
        log.info("ProfileBootstrapMigration: replaying current structural facts to seed SYSTEM-sourced profiles");

        int onboarded = replayEmployees();
        int deptHeads = replayDepartmentHeads();
        int teamHeads = replayTeamChainHeads();
        int projectLeads = replayProjectManagers();

        log.info(
            "ProfileBootstrapMigration completed: onboarded={}, deptHeads={}, teamHeads={}, projectLeads={}",
            onboarded, deptHeads, teamHeads, projectLeads
        );
    }

    private boolean alreadyBootstrapped() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_profile_assignments WHERE assignment_source = 'SYSTEM'",
            Integer.class
        );
        return count != null && count > 0;
    }

    private int replayEmployees() {
        int count = 0;
        for (Employee employee : employeeRepository.findAll()) {
            if (employee.getStatus() != EmployeeStatus.ACTIVE) {
                continue;
            }
            applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
                StructuralEventType.EMPLOYEE_ONBOARDED, employee.getUserId(), employee.getId(), null));
            count++;
        }
        return count;
    }

    private int replayDepartmentHeads() {
        int count = 0;
        for (Department department : departmentRepository.findAll()) {
            if (!department.isActive() || department.getHeadEmployeeId() == null) {
                continue;
            }
            Employee head = employeeRepository.findById(department.getHeadEmployeeId()).orElse(null);
            if (head == null) {
                continue;
            }
            applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
                StructuralEventType.DEPT_HEAD_ASSIGNED, head.getUserId(), department.getId(), null));
            count++;
        }
        return count;
    }

    private int replayTeamChainHeads() {
        int count = 0;
        LocalDate today = LocalDate.now();
        List<TeamHierarchyRelation> relations = teamHierarchyRelationRepository.findAll();
        for (TeamHierarchyRelation relation : relations) {
            if (relation.getStatus() != TeamHierarchyStatus.ACTIVE) {
                continue;
            }
            if (relation.getResponsibleEmployeeId() != null) {
                continue;
            }
            if (relation.getStartDate() != null && relation.getStartDate().isAfter(today)) {
                continue;
            }
            if (relation.getEndDate() != null && relation.getEndDate().isBefore(today)) {
                continue;
            }
            Employee head = employeeRepository.findById(relation.getCollaboratorEmployeeId()).orElse(null);
            if (head == null) {
                continue;
            }
            applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
                StructuralEventType.TEAM_HEAD_ASSIGNED, head.getUserId(), relation.getId(), null));
            count++;
        }
        return count;
    }

    private int replayProjectManagers() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (ProjectAssignment assignment : projectAssignmentRepository.findAll()) {
            if (!assignment.isActive()) {
                continue;
            }
            if (assignment.getAssignmentRole() != ProjectRole.MANAGER) {
                continue;
            }
            if (assignment.getStartDate() != null && assignment.getStartDate().isAfter(today)) {
                continue;
            }
            if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(today)) {
                continue;
            }
            Employee manager = employeeRepository.findById(assignment.getEmployeeId()).orElse(null);
            if (manager == null) {
                continue;
            }
            applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
                StructuralEventType.PROJECT_LEAD_ASSIGNED, manager.getUserId(), assignment.getId(), null));
            count++;
        }
        return count;
    }
}
