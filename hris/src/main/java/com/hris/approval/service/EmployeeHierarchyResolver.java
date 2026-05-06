package com.hris.approval.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeHierarchyResolver {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public Optional<HierarchyApprover> resolveNextApprover(Employee requester) {
        InitialCandidate initialCandidate = resolveInitialCandidate(requester);
        if (initialCandidate == null) {
            return Optional.empty();
        }

        UUID candidateId = initialCandidate.employeeId();
        Employee candidate = initialCandidate.employee();
        boolean departmentHeadCandidate = initialCandidate.departmentHeadCandidate();
        int distance = 1;
        Set<UUID> visited = new HashSet<>();

        while (candidateId != null && visited.add(candidateId)) {
            if (candidate == null) {
                candidate = employeeRepository.findById(candidateId).orElse(null);
            }
            if (candidate == null) {
                return Optional.empty();
            }

            if (!candidate.getId().equals(requester.getId())
                && candidate.getStatus() == EmployeeStatus.ACTIVE) {
                return Optional.of(new HierarchyApprover(
                    candidate,
                    distance,
                    resolveRoleCode(distance, departmentHeadCandidate)
                ));
            }

            candidateId = candidate.getSupervisorEmployeeId();
            candidate = null;
            departmentHeadCandidate = false;
            distance++;
        }

        return Optional.empty();
    }

    private InitialCandidate resolveInitialCandidate(Employee requester) {
        if (requester.getSupervisorEmployeeId() != null) {
            return new InitialCandidate(requester.getSupervisorEmployeeId(), null, false);
        }
        if (requester.getDepartmentId() == null) {
            return null;
        }
        return departmentRepository.findDepartmentHead(requester.getDepartmentId())
            .map(head -> new InitialCandidate(head.getId(), head, true))
            .orElse(null);
    }

    private String resolveRoleCode(int distance, boolean departmentHeadCandidate) {
        if (departmentHeadCandidate) {
            return "DEPT_HEAD";
        }
        return "N_PLUS_" + distance;
    }

    public record HierarchyApprover(Employee employee, int distance, String roleCode) {
    }

    private record InitialCandidate(UUID employeeId, Employee employee, boolean departmentHeadCandidate) {
    }
}
