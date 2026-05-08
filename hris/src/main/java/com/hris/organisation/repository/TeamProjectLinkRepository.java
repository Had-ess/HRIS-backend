package com.hris.organisation.repository;

import com.hris.organisation.entity.TeamProjectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamProjectLinkRepository extends JpaRepository<TeamProjectLink, UUID> {
    Optional<TeamProjectLink> findByTeamIdAndProjectId(UUID teamId, UUID projectId);
}
