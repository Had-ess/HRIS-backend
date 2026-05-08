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
        join TeamProjectLink tpl on tpl.teamId = t.id
        where tpl.projectId = :projectId
          and tpl.isActive = true
          and t.isActive = true
        order by t.name asc
        """)
    List<Team> findActiveByProjectId(@Param("projectId") UUID projectId);

    @Query("""
        select distinct tpl.projectId
        from TeamProjectLink tpl
        join Team t on t.id = tpl.teamId
        where t.supervisorEmployeeId = :supervisorEmployeeId
          and tpl.isActive = true
          and t.isActive = true
        """)
    List<UUID> findProjectIdsBySupervisorEmployeeId(@Param("supervisorEmployeeId") UUID supervisorEmployeeId);
}
