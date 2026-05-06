package com.hris.organisation.repository;

import com.hris.organisation.entity.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, UUID> {

    List<ProjectTeam> findByProjectIdAndIsActiveTrue(UUID projectId);

    @Query("""
        select distinct pt.projectId
        from ProjectTeam pt
        where pt.supervisorEmployeeId = :supervisorEmployeeId
          and pt.isActive = true
        """)
    List<UUID> findProjectIdsBySupervisorEmployeeId(@Param("supervisorEmployeeId") UUID supervisorEmployeeId);
}
