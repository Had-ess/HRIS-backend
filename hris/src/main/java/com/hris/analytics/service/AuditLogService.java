package com.hris.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.entity.AuditLog;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID actorId, AuditAction action, String resource,
                    UUID resourceId, Object previousState, Object newState) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .resource(resource)
                .resourceId(resourceId)
                .previousState(serializeState(previousState))
                .newState(serializeState(newState))
                .timestamp(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log entry", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getByResource(String resource, Pageable pageable) {
        return auditLogRepository.findByResource(resource, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getByActor(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(
            String resource,
            AuditAction action,
            UUID actorId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
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

        return auditLogRepository.findAll(specification, pageable);
    }

    private String serializeState(Object state) {
        if (state == null) return null;
        if (state instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            return state.toString();
        }
    }
}
