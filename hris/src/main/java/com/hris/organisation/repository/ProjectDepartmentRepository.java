package com.hris.organisation.repository;

import com.hris.organisation.entity.ProjectDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectDepartmentRepository extends JpaRepository<ProjectDepartment, UUID> {

    List<ProjectDepartment> findByProjectId(UUID projectId);
}
