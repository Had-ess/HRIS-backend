package com.hris.approval.service;

import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamHierarchyResolver Unit Tests")
class TeamHierarchyResolverTest {

    @Mock
    private TeamHierarchyRelationRepository relationRepository;

    private TeamHierarchyResolver resolver;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        resolver = new TeamHierarchyResolver(relationRepository);
        teamId = UUID.randomUUID();
    }

    @Test
    @DisplayName("resolveAboveRequester returns validator chain in direct order")
    void resolveAboveRequesterReturnsValidatorChainInDirectOrder() {
        UUID head = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID requester = UUID.randomUUID();

        when(relationRepository.findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(
                relation(teamId, null, head),
                relation(teamId, head, manager),
                relation(teamId, manager, requester)
            ));

        TeamHierarchyResolver.RouteCandidateList result =
            resolver.resolveAboveRequester(teamId, requester, LocalDate.of(2026, 5, 8));

        assertThat(result.validatorEmployeeIds()).containsExactly(manager, head);
        assertThat(result.noValidator()).isFalse();
    }

    @Test
    @DisplayName("resolveAboveRequester returns no-validator marker for top of chain requester")
    void resolveAboveRequesterReturnsNoValidatorMarkerForTopOfChainRequester() {
        UUID requester = UUID.randomUUID();

        when(relationRepository.findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(relation(teamId, null, requester)));

        TeamHierarchyResolver.RouteCandidateList result =
            resolver.resolveAboveRequester(teamId, requester, LocalDate.of(2026, 5, 8));

        assertThat(result.validatorEmployeeIds()).isEmpty();
        assertThat(result.noValidator()).isTrue();
    }

    private TeamHierarchyRelation relation(UUID teamId, UUID responsibleEmployeeId, UUID collaboratorEmployeeId) {
        return TeamHierarchyRelation.builder()
            .id(UUID.randomUUID())
            .teamId(teamId)
            .responsibleEmployeeId(responsibleEmployeeId)
            .collaboratorEmployeeId(collaboratorEmployeeId)
            .status(TeamHierarchyStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 1, 1))
            .build();
    }
}
