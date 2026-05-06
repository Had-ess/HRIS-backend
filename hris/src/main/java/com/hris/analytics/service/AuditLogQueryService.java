package com.hris.analytics.service;

import com.hris.analytics.dto.AuditLogDto;
import com.hris.analytics.entity.AuditLog;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.AuditLogRepository;
import com.hris.auth.service.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final UserDisplayNameService userDisplayNameService;

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAll(Pageable pageable) {
        return toDtoPage(auditLogRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getByResource(String resource, Pageable pageable) {
        return toDtoPage(auditLogRepository.findByResource(resource, pageable));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(
            String resource,
            AuditAction action,
            UUID actorId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        Specification<AuditLog> specification = Specification.where(null);

        if (resource != null && !resource.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("resource"), resource));
        }
        if (action != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (actorId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) ->
                cb.lessThan(root.get("timestamp"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }

        return toDtoPage(auditLogRepository.findAll(specification, pageable));
    }

    private Page<AuditLogDto> toDtoPage(Page<AuditLog> auditLogs) {
        Map<UUID, String> actorNames = resolveActorNames(auditLogs.getContent());
        return new PageImpl<>(
            auditLogs.getContent().stream()
                .map(auditLog -> toDto(auditLog, actorNames))
                .toList(),
            auditLogs.getPageable(),
            auditLogs.getTotalElements()
        );
    }

    private AuditLogDto toDto(AuditLog auditLog, Map<UUID, String> actorNames) {
        return new AuditLogDto(
            auditLog.getId(),
            auditLog.getActorId(),
            actorNames.get(auditLog.getActorId()),
            auditLog.getAction(),
            auditLog.getResource(),
            auditLog.getResourceId(),
            auditLog.getPreviousState(),
            auditLog.getNewState(),
            auditLog.getIpAddress(),
            auditLog.getTimestamp()
        );
    }

    private Map<UUID, String> resolveActorNames(java.util.List<AuditLog> auditLogs) {
        return userDisplayNameService.resolveDisplayNames(auditLogs.stream()
            .map(AuditLog::getActorId)
            .toList());
    }
}
