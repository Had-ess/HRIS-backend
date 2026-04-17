package com.hris.analytics.service;

import com.hris.analytics.entity.LeaveMetrics;
import com.hris.analytics.enums.ScopeType;
import com.hris.analytics.repository.LeaveMetricsRepository;
import com.hris.common.ScopeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveMetricsService {

    private final LeaveMetricsRepository leaveMetricsRepository;

    @Transactional(readOnly = true)
    public List<LeaveMetrics> getByPeriod(String period, ScopeFilter scope) {
        if (scope.type() == ScopeType.ALL || scope.type() == ScopeType.PROJECT || scope.entityId() == null) {
            return leaveMetricsRepository.findByPeriod(period);
        }
        return leaveMetricsRepository.findByPeriodAndDepartmentId(period, scope.entityId());
    }
}
