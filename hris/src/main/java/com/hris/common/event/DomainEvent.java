package com.hris.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard domain event envelope used for logging, audit trails, and analytics hooks.
 * This is a non-persisted value object that wraps business event metadata.
 */
public record DomainEvent(
    UUID eventId,
    DomainEventType eventType,
    String aggregateType,
    UUID aggregateId,
    UUID actorUserId,
    ActorType actorType,
    UUID correlationId,
    Instant occurredAt,
    Map<String, Object> payload,
    int schemaVersion
) {

    public static DomainEvent create(
            DomainEventType eventType,
            String aggregateType,
            UUID aggregateId,
            UUID actorUserId,
            ActorType actorType,
            Map<String, Object> payload) {
        return new DomainEvent(
            UUID.randomUUID(),
            eventType,
            aggregateType,
            aggregateId,
            actorUserId,
            actorType,
            UUID.randomUUID(),
            Instant.now(),
            payload != null ? Map.copyOf(payload) : Map.of(),
            1
        );
    }

    public static DomainEvent systemEvent(
            DomainEventType eventType,
            String aggregateType,
            UUID aggregateId,
            Map<String, Object> payload) {
        return create(eventType, aggregateType, aggregateId, SystemActor.SYSTEM_ACTOR_ID, ActorType.SYSTEM, payload);
    }
}
