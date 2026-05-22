package com.hris.analytics.service;

import com.hris.analytics.dto.AnalyticsAdminRequestReportDto;
import com.hris.analytics.dto.AnalyticsCountDto;
import com.hris.analytics.dto.AnalyticsDateCountDto;
import com.hris.analytics.dto.AnalyticsOverviewDto;
import com.hris.analytics.dto.AnalyticsSummaryDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private AnalyticsQueryService analyticsQueryService;

    @Test
    @DisplayName("summary aggregates leave approval balance and SLA counts from transactional queries")
    void summaryAggregatesTransactionalCounts() {
        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Long.class)))
            .thenReturn(12L, 4L, 3L, 2L, 40L, 1L, 5L);

        AnalyticsSummaryDto result = analyticsQueryService.getSummary(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );

        assertThat(result.leaveRequests()).isEqualTo(12L);
        assertThat(result.pendingLeaveRequests()).isEqualTo(4L);
        assertThat(result.pendingApprovals()).isEqualTo(3L);
        assertThat(result.pendingAdminRequests()).isEqualTo(2L);
        assertThat(result.availableBalanceDays()).isEqualTo(40L);
        assertThat(result.negativeBalances()).isEqualTo(1L);
        assertThat(result.slaExceededAdminRequests()).isEqualTo(5L);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("admin request analytics returns counts SLA metrics and distributions")
    void adminRequestAnalyticsReturnsCountsAndDistributions() {
        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Long.class)))
            .thenReturn(9L, 3L, 2L, 1L, 1L, 5L, 2L);
        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Double.class)))
            .thenReturn(18.5);
        when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(
                List.of(new AnalyticsCountDto("SUBMITTED", "SUBMITTED", 3L)),
                List.of(new AnalyticsCountDto("CERT", "Certificate", 4L)),
                List.of(new AnalyticsDateCountDto(LocalDate.of(2026, 5, 2), 2L))
            );

        AnalyticsAdminRequestReportDto result = analyticsQueryService.getAdminRequests(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );

        assertThat(result.totalRequests()).isEqualTo(9L);
        assertThat(result.submittedCount()).isEqualTo(3L);
        assertThat(result.inReviewCount()).isEqualTo(2L);
        assertThat(result.completedCount()).isEqualTo(1L);
        assertThat(result.rejectedCount()).isEqualTo(1L);
        assertThat(result.pendingCount()).isEqualTo(5L);
        assertThat(result.averageProcessingHours()).isEqualTo(18.5);
        assertThat(result.slaExceededCount()).isEqualTo(2L);
        assertThat(result.byStatus()).hasSize(1);
        assertThat(result.byType()).hasSize(1);
        assertThat(result.overTime()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("overview aggregates dashboard KPIs headcount distribution bottlenecks and leave reasons")
    void overviewAggregatesScreenshotDashboardSections() {
        UUID employeeId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Long.class)))
            .thenReturn(
                105L,
                96L, 98L, 99L, 100L, 101L, 102L, 103L, 104L, 105L, 105L, 106L, 107L,
                837L,
                14L,
                1200L,
                816L
            );
        when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(
                List.of(
                    new AnalyticsCountDto("ANNUAL", "Annual leave", 412L),
                    new AnalyticsCountDto("SICK", "Sick leave", 425L)
                ),
                List.of(new com.hris.analytics.dto.AnalyticsApprovalBottleneckDto(employeeId, "Direct manager", 8L, 6.2, 0L)),
                List.of(new AnalyticsCountDto("family vacation", "Family vacation", 142L))
            );

        AnalyticsOverviewDto result = analyticsQueryService.getOverview(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );

        assertThat(result.kpis()).extracting("key")
            .containsExactly("headcount", "totalLeaveDays", "averageUtilization", "openRequests");
        assertThat(result.kpis().get(0).value()).isEqualTo("105");
        assertThat(result.kpis().get(1).value()).isEqualTo("837");
        assertThat(result.kpis().get(3).value()).isEqualTo("14");
        assertThat(result.headcountTrend()).hasSize(12);
        assertThat(result.leaveByType()).first().extracting("label", "days", "percentage")
            .containsExactly("Annual leave", 412L, 49);
        assertThat(result.approvalBottlenecks()).first().extracting("label", "value", "detail")
            .containsExactly("Direct manager", 8L, "6.2h avg");
        assertThat(result.topLeaveReasons()).first().extracting("label", "value", "percentage")
            .containsExactly("Family vacation", 142L, 100);
    }
}
