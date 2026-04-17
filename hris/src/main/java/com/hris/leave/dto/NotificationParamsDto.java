package com.hris.leave.dto;

public record NotificationParamsDto(
    String employeeName,
    String startDate,
    String endDate,
    int workingDays
) {}
