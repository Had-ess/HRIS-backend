package com.hris.organisation.repository;

import com.hris.organisation.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Page<Team> findAllByOrderByNameAsc(Pageable pageable);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<Team> findBySupervisorEmployeeIdAndIsActiveTrue(UUID supervisorEmployeeId);

    @Query("""
        select t
        from Team t
        where t.projectId = :projectId
          and t.isActive = true
        order by t.name asc
        """)
    List<Team> findActiveByProjectId(@Param("projectId") UUID projectId);

    @Query("""
        select distinct t.projectId
        from Team t
        where t.supervisorEmployeeId = :supervisorEmployeeId
          and t.isActive = true
        """)
    List<UUID> findProjectIdsBySupervisorEmployeeId(@Param("supervisorEmployeeId") UUID supervisorEmployeeId);

    Page<Team> findByDepartmentIdInOrderByNameAsc(List<UUID> departmentIds, Pageable pageable);

    Page<Team> findByDepartmentIdOrderByNameAsc(UUID departmentId, Pageable pageable);
}
