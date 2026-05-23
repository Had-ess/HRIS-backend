package com.hris.organisation.repository;

import com.hris.organisation.entity.ProjectAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pa FROM ProjectAssignment pa WHERE pa.id = :id")
    Optional<ProjectAssignment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT pa FROM ProjectAssignment pa
        WHERE pa.employeeId = :eid
          AND pa.startDate <= :endDate
          AND (pa.endDate >= :startDate OR pa.endDate IS NULL)
          AND pa.isActive = true
        ORDER BY pa.projectId ASC, pa.startDate ASC, pa.id ASC
        """)
    List<ProjectAssignment> findActiveAssignmentsDuringPeriod(
        @Param("eid") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT pa FROM ProjectAssignment pa
        WHERE pa.employeeId = :employeeId
          AND pa.projectId = :projectId
          AND pa.startDate <= :endDate
          AND (pa.endDate >= :startDate OR pa.endDate IS NULL)
          AND pa.isActive = true
        ORDER BY pa.startDate ASC, pa.id ASC
        """)
    List<ProjectAssignment> findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
        @Param("employeeId") UUID employeeId,
        @Param("projectId") UUID projectId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Modifying
    @Query("UPDATE ProjectAssignment pa SET pa.isActive = false WHERE pa.endDate < CURRENT_DATE AND pa.isActive = true")
    int deactivateExpired();

    List<ProjectAssignment> findByEmployeeIdAndIsActiveTrue(UUID employeeId);

    long countByTeamIdAndIsActiveTrue(UUID teamId);

    @Modifying
    @Query("UPDATE ProjectAssignment pa SET pa.teamId = null WHERE pa.teamId = :teamId")
    int clearTeamReference(@Param("teamId") UUID teamId);

    boolean existsByEmployeeId(UUID employeeId);

    boolean existsBySupervisorId(UUID supervisorId);

    List<ProjectAssignment> findByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);

    @Query(value = """
        SELECT COUNT(*)
        FROM project_assignments pa
        WHERE pa.project_id = :projectId
          AND pa.is_active = true
        """, nativeQuery = true)
    long countActiveByProjectId(@Param("projectId") UUID projectId);

    boolean existsByProjectIdAndIsActiveTrue(UUID projectId);

    @Query("""
        select new com.hris.organisation.dto.ProjectAssignmentViewDto(
            pa.id,
            e.id,
            e.userId,
            e.employeeCode,
            concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, '')),
            pa.projectId,
            pa.teamId,
            t.name,
            s.id,
            s.userId,
            s.employeeCode,
            concat(coalesce(su.firstName, ''), ' ', coalesce(su.lastName, '')),
            pa.assignmentRole,
            pa.startDate,
            pa.endDate,
            pa.isActive
        )
        from ProjectAssignment pa
        join Employee e on e.id = pa.employeeId
        join User u on u.id = e.userId
        left join Team t on t.id = pa.teamId
        join Employee s on s.id = pa.supervisorId
        join User su on su.id = s.userId
        where pa.projectId = :projectId
          and pa.isActive = true
        order by pa.startDate desc, u.firstName asc, u.lastName asc
        """)
    List<com.hris.organisation.dto.ProjectAssignmentViewDto> findActiveViewsByProjectId(
        @Param("projectId") UUID projectId
    );

    @Query("""
        SELECT DISTINCT pa.projectId
        FROM ProjectAssignment pa
        WHERE pa.employeeId = :employeeId
          AND pa.isActive = true
          AND pa.startDate <= :today
          AND (pa.endDate IS NULL OR pa.endDate >= :today)
        """)
    List<UUID> findActiveProjectIdsByEmployeeId(
        @Param("employeeId") UUID employeeId,
        @Param("today") LocalDate today);

    @Query("""
        SELECT DISTINCT pa.projectId
        FROM ProjectAssignment pa
        WHERE pa.supervisorId = :supervisorId
          AND pa.isActive = true
          AND pa.startDate <= :today
          AND (pa.endDate IS NULL OR pa.endDate >= :today)
        """)
    List<UUID> findActiveProjectIdsBySupervisorId(
        @Param("supervisorId") UUID supervisorId,
        @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(DISTINCT pa.employeeId)
        FROM ProjectAssignment pa
        WHERE pa.supervisorId = :supervisorId
          AND pa.isActive = true
          AND pa.startDate <= :today
          AND (pa.endDate IS NULL OR pa.endDate >= :today)
        """)
    long countActiveDistinctEmployeesBySupervisorId(
        @Param("supervisorId") UUID supervisorId,
        @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(pa)
        FROM ProjectAssignment pa
        WHERE pa.projectId IN (
            SELECT pd.projectId
            FROM ProjectDepartment pd
            WHERE pd.departmentId = :departmentId
        )
          AND pa.isActive = true
          AND pa.startDate <= :today
          AND (pa.endDate IS NULL OR pa.endDate >= :today)
        """)
    long countActiveByDepartmentId(
        @Param("departmentId") UUID departmentId,
        @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(pa) FROM ProjectAssignment pa
        WHERE pa.employeeId = :employeeId
          AND pa.projectId = :projectId
          AND pa.isActive = true
          AND pa.startDate <= :endDate
          AND (pa.endDate IS NULL OR pa.endDate >= :startDate)
        """)
    long countOverlappingActiveAssignments(
        @Param("employeeId") UUID employeeId,
        @Param("projectId") UUID projectId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT COUNT(pa) FROM ProjectAssignment pa
        WHERE pa.employeeId = :employeeId
          AND pa.projectId = :projectId
          AND pa.isActive = true
          AND (pa.endDate IS NULL OR pa.endDate >= :startDate)
        """)
    long countOverlappingActiveAssignmentsOpenEnded(
        @Param("employeeId") UUID employeeId,
        @Param("projectId") UUID projectId,
        @Param("startDate") LocalDate startDate);
}
