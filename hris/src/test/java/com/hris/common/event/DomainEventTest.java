package com.hris.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DomainEvent Unit Tests")
class DomainEventTest {

    @Test
    @DisplayName("create() should populate all fields with defaults")
    void createShouldPopulateAllFields() {
        UUID aggregateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("key", "value");

        DomainEvent event = DomainEvent.create(
            DomainEventType.LEAVE_REQUEST_SUBMITTED,
            "leave_request",
            aggregateId,
            actorId,
            ActorType.USER,
            payload
        );

        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo(DomainEventType.LEAVE_REQUEST_SUBMITTED);
        assertThat(event.aggregateType()).isEqualTo("leave_request");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.actorUserId()).isEqualTo(actorId);
        assertThat(event.actorType()).isEqualTo(ActorType.USER);
        assertThat(event.correlationId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.payload()).containsEntry("key", "value");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("systemEvent() should use SYSTEM actor type and ID")
    void systemEventShouldUseSystemActor() {
        UUID aggregateId = UUID.randomUUID();

        DomainEvent event = DomainEvent.systemEvent(
            DomainEventType.LEAVE_ACCRUAL_APPLIED,
            "leave_accrual_run",
            aggregateId,
            Map.of("count", 5)
        );

        assertThat(event.actorUserId()).isEqualTo(SystemActor.SYSTEM_ACTOR_ID);
        assertThat(event.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.eventType()).isEqualTo(DomainEventType.LEAVE_ACCRUAL_APPLIED);
        assertThat(event.payload()).containsEntry("count", 5);
    }

    @Test
    @DisplayName("create() with null payload should default to empty map")
    void createWithNullPayloadShouldDefaultToEmptyMap() {
        DomainEvent event = DomainEvent.create(
            DomainEventType.ADMIN_REQUEST_CREATED,
            "admin_request",
            UUID.randomUUID(),
            UUID.randomUUID(),
            ActorType.USER,
            null
        );

        assertThat(event.payload()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("SystemActor constants should be well-defined")
    void systemActorConstantsShouldBeWellDefined() {
        assertThat(SystemActor.SYSTEM_ACTOR_ID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertThat(SystemActor.SYSTEM_ACTOR_NAME).isEqualTo("SYSTEM");
        assertThat(SystemActor.isSystemActor(SystemActor.SYSTEM_ACTOR_ID)).isTrue();
        assertThat(SystemActor.isSystemActor(UUID.randomUUID())).isFalse();
    }
}
