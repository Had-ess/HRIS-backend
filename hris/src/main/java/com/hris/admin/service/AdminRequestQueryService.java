package com.hris.admin.service;

import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.mapper.AdminRequestMapper;
import com.hris.auth.service.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestQueryService {

    private final AdminRequestMapper adminRequestMapper;
    private final UserDisplayNameService userDisplayNameService;

    @Transactional(readOnly = true)
    public Page<AdminRequestResponseDto> toDtoPage(Page<AdminRequest> requests) {
        Map<UUID, String> requesterNames = userDisplayNameService.resolveDisplayNames(
            requests.getContent().stream()
                .map(AdminRequest::getRequesterId)
                .toList()
        );
        return requests.map(request -> toDto(request, requesterNames));
    }

    @Transactional(readOnly = true)
    public AdminRequestResponseDto toDto(AdminRequest request) {
        return toDto(request, userDisplayNameService.resolveDisplayNames(
            java.util.List.of(request.getRequesterId())));
    }

    private AdminRequestResponseDto toDto(AdminRequest request, Map<UUID, String> requesterNames) {
        AdminRequestResponseDto dto = adminRequestMapper.toDto(request);
        return new AdminRequestResponseDto(
            dto.id(),
            dto.requesterId(),
            requesterNames.get(dto.requesterId()),
            dto.requestTypeId(),
            dto.trackingNumber(),
            dto.description(),
            dto.urgencyLevel(),
            dto.status(),
            dto.metadata(),
            dto.rejectionReason(),
            dto.submittedAt(),
            dto.resolvedAt(),
            dto.resolvedById()
        );
    }
}
