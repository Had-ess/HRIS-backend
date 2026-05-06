package com.hris.organisation.repository;

import com.hris.organisation.entity.ProjectDepartment;
import com.hris.organisation.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectDepartmentRepository extends JpaRepository<ProjectDepartment, UUID> {

    List<ProjectDepartment> findByProjectId(UUID projectId);

    Optional<ProjectDepartment> findByProjectIdAndDepartmentId(UUID projectId, UUID departmentId);

    boolean existsByProjectIdAndDepartmentId(UUID projectId, UUID departmentId);

    long countByDepartmentId(UUID departmentId);

    @Query("""
        select distinct pd.projectId
        from ProjectDepartment pd
        where pd.departmentId = :departmentId
        """)
    List<UUID> findProjectIdsByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query("""
        select count(pd) > 0
        from ProjectDepartment pd
        join Project p on p.id = pd.projectId
        where pd.departmentId = :departmentId and p.status = :status
        """)
    boolean existsByDepartmentIdAndProjectStatus(
        @Param("departmentId") UUID departmentId,
        @Param("status") ProjectStatus status
    );
}
