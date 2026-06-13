package com.hris.organisation.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.organisation.dto.OrgChartDtos.DepartmentNode;
import com.hris.organisation.dto.OrgChartDtos.SpineNode;
import com.hris.organisation.dto.OrgChartDtos.TeamNode;
import com.hris.organisation.entity.Team;
import com.hris.organisation.repository.TeamRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Org chart read model (ORG_BACKBONE_DESIGN.md §7): the department tree built
 * from parent_department_id and the supervisor forest built from the canonical
 * spine. Department-scoped callers only see their scope.
 */
@Service
@RequiredArgsConstructor
public class OrgChartService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AccessScopeService accessScopeService;

    @Transactional(readOnly = true)
    public List<DepartmentNode> getDepartmentChart(UUID userId) {
        List<Department> visible = visibleDepartments(userId);
        Set<UUID> visibleIds = visible.stream().map(Department::getId).collect(Collectors.toSet());

        Map<UUID, List<Department>> childrenByParent = new HashMap<>();
        List<Department> roots = new ArrayList<>();
        for (Department department : visible) {
            UUID parentId = department.getParentDepartmentId();
            if (parentId != null && visibleIds.contains(parentId)) {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(department);
            } else {
                roots.add(department);
            }
        }

        Comparator<Department> byName = Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER);
        roots.sort(byName);
        childrenByParent.values().forEach(list -> list.sort(byName));

        return roots.stream()
            .map(root -> toDepartmentNode(root, childrenByParent))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SpineNode> getSpine(UUID userId) {
        List<Employee> employees = visibleEmployees(userId).stream()
            .filter(employee -> employee.getStatus() != EmployeeStatus.TERMINATED)
            .toList();
        Set<UUID> employeeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());

        Map<UUID, String> userNames = userRepository.findAllById(
                employees.stream().map(Employee::getUserId).filter(java.util.Objects::nonNull).toList())
            .stream()
            .collect(Collectors.toMap(User::getId, OrgChartService::displayName, (a, b) -> a));
        Map<UUID, String> departmentNames = departmentRepository.findAll().stream()
            .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        Map<UUID, List<Employee>> childrenBySupervisor = new HashMap<>();
        List<Employee> roots = new ArrayList<>();
        for (Employee employee : employees) {
            UUID supervisorId = employee.getSupervisorEmployeeId();
            if (supervisorId != null && employeeIds.contains(supervisorId)) {
                childrenBySupervisor.computeIfAbsent(supervisorId, k -> new ArrayList<>()).add(employee);
            } else {
                roots.add(employee);
            }
        }

        Comparator<Employee> byName = Comparator.comparing(
            employee -> employeeName(employee, userNames), String.CASE_INSENSITIVE_ORDER);
        roots.sort(byName);
        childrenBySupervisor.values().forEach(list -> list.sort(byName));

        Set<UUID> guard = new HashSet<>();
        return roots.stream()
            .map(root -> toSpineNode(root, childrenBySupervisor, userNames, departmentNames, guard))
            .toList();
    }

    private DepartmentNode toDepartmentNode(Department department, Map<UUID, List<Department>> childrenByParent) {
        String headName = department.getHeadEmployeeId() != null
            ? employeeRepository.findById(department.getHeadEmployeeId())
                .map(this::resolveEmployeeName)
                .orElse(null)
            : null;

        List<TeamNode> teams = teamRepository
            .findByDepartmentIdAndIsActiveTrueOrderByNameAsc(department.getId())
            .stream()
            .map(this::toTeamNode)
            .toList();

        List<DepartmentNode> children = childrenByParent
            .getOrDefault(department.getId(), List.of())
            .stream()
            .map(child -> toDepartmentNode(child, childrenByParent))
            .toList();

        return new DepartmentNode(
            department.getId(),
            department.getName(),
            department.getCode(),
            department.getHeadEmployeeId(),
            headName,
            employeeRepository.countByDepartmentId(department.getId()),
            teams,
            children
        );
    }

    private TeamNode toTeamNode(Team team) {
        String supervisorName = employeeRepository.findById(team.getSupervisorEmployeeId())
            .map(this::resolveEmployeeName)
            .orElse(null);
        return new TeamNode(team.getId(), team.getName(), supervisorName);
    }

    private SpineNode toSpineNode(Employee employee, Map<UUID, List<Employee>> childrenBySupervisor,
                                  Map<UUID, String> userNames, Map<UUID, String> departmentNames,
                                  Set<UUID> guard) {
        if (!guard.add(employee.getId())) {
            // defensive: data cycles should be impossible since the cycle check, but
            // never let one take the endpoint down
            return new SpineNode(employee.getId(), employeeName(employee, userNames),
                employee.getJobTitle(), departmentNames.get(employee.getDepartmentId()), List.of());
        }
        List<SpineNode> children = childrenBySupervisor
            .getOrDefault(employee.getId(), List.of())
            .stream()
            .map(child -> toSpineNode(child, childrenBySupervisor, userNames, departmentNames, guard))
            .toList();
        return new SpineNode(
            employee.getId(),
            employeeName(employee, userNames),
            employee.getJobTitle(),
            departmentNames.get(employee.getDepartmentId()),
            children
        );
    }

    private List<Department> visibleDepartments(UUID userId) {
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(userId);
        if (scope.isGlobal()) {
            return departmentRepository.findAll();
        }
        if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
            return departmentRepository.findAllById(scope.departmentIds());
        }
        return List.of();
    }

    private List<Employee> visibleEmployees(UUID userId) {
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(userId);
        if (scope.isGlobal()) {
            return employeeRepository.findAll();
        }
        if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
            return scope.departmentIds().stream()
                .flatMap(departmentId -> employeeRepository.findByDepartmentId(departmentId).stream())
                .toList();
        }
        return accessScopeService.findEmployee(userId).map(List::of).orElse(List.of());
    }

    private String resolveEmployeeName(Employee employee) {
        return userRepository.findById(employee.getUserId())
            .map(OrgChartService::displayName)
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private String employeeName(Employee employee, Map<UUID, String> userNames) {
        String name = employee.getUserId() != null ? userNames.get(employee.getUserId()) : null;
        return name != null && !name.isBlank() ? name : employee.getEmployeeCode();
    }

    private static String displayName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
}
