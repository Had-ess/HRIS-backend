package com.hris.admin.mapper;

import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.leave.enums.UrgencyLevel;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-17T21:11:49+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class AdminRequestMapperImpl implements AdminRequestMapper {

    @Override
    public AdminRequestResponseDto toDto(AdminRequest request) {
        if ( request == null ) {
            return null;
        }

        UUID id = null;
        UUID requesterId = null;
        UUID requestTypeId = null;
        String trackingNumber = null;
        String description = null;
        UrgencyLevel urgencyLevel = null;
        AdminRequestStatus status = null;
        String metadata = null;
        String rejectionReason = null;
        Instant submittedAt = null;
        Instant resolvedAt = null;
        UUID resolvedById = null;

        id = request.getId();
        requesterId = request.getRequesterId();
        requestTypeId = request.getRequestTypeId();
        trackingNumber = request.getTrackingNumber();
        description = request.getDescription();
        urgencyLevel = request.getUrgencyLevel();
        status = request.getStatus();
        metadata = request.getMetadata();
        rejectionReason = request.getRejectionReason();
        submittedAt = request.getSubmittedAt();
        resolvedAt = request.getResolvedAt();
        resolvedById = request.getResolvedById();

        AdminRequestResponseDto adminRequestResponseDto = new AdminRequestResponseDto( id, requesterId, requestTypeId, trackingNumber, description, urgencyLevel, status, metadata, rejectionReason, submittedAt, resolvedAt, resolvedById );

        return adminRequestResponseDto;
    }

    @Override
    public AdminRequestTypeDto toTypeDto(AdminRequestType type) {
        if ( type == null ) {
            return null;
        }

        boolean isActive = false;
        UUID id = null;
        String code = null;
        String name = null;

        isActive = type.isActive();
        id = type.getId();
        code = type.getCode();
        name = type.getName();

        AdminRequestTypeDto adminRequestTypeDto = new AdminRequestTypeDto( id, code, name, isActive );

        return adminRequestTypeDto;
    }
}
