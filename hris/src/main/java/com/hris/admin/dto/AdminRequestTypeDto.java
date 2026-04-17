package com.hris.admin.dto;
import java.util.UUID;
public record AdminRequestTypeDto(UUID id, String code, String name, boolean isActive) {}
