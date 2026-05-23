package com.hris.organisation.hierarchy.repository;

import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamHierarchyRelationRepository extends JpaRepository<TeamHierarchyRelation, UUID> {

    List<TeamHierarchyRelation> findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(
        UUID teamId,
        TeamHierarchyStatus status
    );

    List<TeamHierarchyRelation> findByCollaboratorEmployeeIdAndStatusOrderByStartDateAscTeamIdAsc(
        UUID collaboratorEmployeeId,
        TeamHierarchyStatus status
    );

    boolean existsByTeamId(UUID teamId);

    void deleteByTeamId(UUID teamId);

    void deleteByTeamIdIn(List<UUID> teamIds);
}
