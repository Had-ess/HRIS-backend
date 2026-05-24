package com.hris.organisation.repository;

import com.hris.organisation.entity.TeamProjectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamProjectLinkRepository extends JpaRepository<TeamProjectLink, UUID> {
    Optional<TeamProjectLink> findByTeamIdAndProjectId(UUID teamId, UUID projectId);
    void deleteByProjectId(UUID projectId);
    void deleteByTeamId(UUID teamId);

    @Query("""
        SELECT DISTINCT tpl.teamId
        FROM TeamProjectLink tpl
        WHERE tpl.teamId IN :teamIds
          AND tpl.isActive = true
          AND (tpl.startDate IS NULL OR tpl.startDate <= :endDate)
          AND (tpl.endDate IS NULL OR tpl.endDate >= :startDate)
        ORDER BY tpl.teamId ASC
        """)
    List<UUID> findActiveTeamIdsDuringPeriod(
        @Param("teamIds") List<UUID> teamIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}
