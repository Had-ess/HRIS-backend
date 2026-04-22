package com.hris.organisation.repository;

import com.hris.organisation.entity.Project;
import com.hris.organisation.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByCode(String code);

    long countByStatus(ProjectStatus status);

    Page<Project> findByIdIn(Collection<UUID> ids, Pageable pageable);

    @Query("""
        select distinct p
        from Project p
        join ProjectDepartment pd on pd.projectId = p.id
        where pd.departmentId = :departmentId
        """)
    Page<Project> findByDepartmentId(@Param("departmentId") UUID departmentId, Pageable pageable);

    @Query("""
        select p
        from Project p
        where p.id = :projectId
          and p.id in :projectIds
        """)
    Optional<Project> findScopedByProjectIds(
        @Param("projectId") UUID projectId,
        @Param("projectIds") Collection<UUID> projectIds
    );

    @Query("""
        select distinct p
        from Project p
        join ProjectDepartment pd on pd.projectId = p.id
        where p.id = :projectId
          and pd.departmentId = :departmentId
        """)
    Optional<Project> findScopedByDepartmentId(
        @Param("projectId") UUID projectId,
        @Param("departmentId") UUID departmentId
    );
}
