package com.hris.dashboard.dto;

import java.util.List;

public record SupervisorDashboardDto(
    long pendingApprovalsCount,
    List<ApprovalSummaryDto> recentPendingApprovals,
    long teamMembersCount,
    long upcomingTeamAbsencesCount
) {}
