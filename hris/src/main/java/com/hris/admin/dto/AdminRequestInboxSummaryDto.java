package com.hris.admin.dto;

public record AdminRequestInboxSummaryDto(
    long submitted,
    long inReview,
    long overdue,
    long completed
) {
}
