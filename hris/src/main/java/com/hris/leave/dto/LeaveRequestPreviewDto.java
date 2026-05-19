package com.hris.leave.dto;

import java.math.BigDecimal;
import java.util.List;

public record LeaveRequestPreviewDto(
    BigDecimal durationDays,
    BigDecimal durationHours,
    Integer workingDays,
    BigDecimal currentBalance,
    BigDecimal projectedBalance,
    boolean sufficientBalance,
    Integer workHoursPerDay,
    List<String> warnings
) {
}
