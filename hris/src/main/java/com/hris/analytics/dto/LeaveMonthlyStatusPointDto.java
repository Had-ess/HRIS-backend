package com.hris.analytics.dto;

public record LeaveMonthlyStatusPointDto(int month, long approved, long rejected, long pending) {
}
