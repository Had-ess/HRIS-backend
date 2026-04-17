package com.hris.organisation.repository;

import com.hris.organisation.entity.Project;
import com.hris.organisation.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByCode(String code);

    long countByStatus(ProjectStatus status);
}
