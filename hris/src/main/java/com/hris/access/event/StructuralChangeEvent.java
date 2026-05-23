package com.hris.access.event;

import com.hris.access.enums.StructuralEventType;

import java.util.UUID;

/**
 * In-process structural change signal consumed by the automated profile
 * assignment engine. The {@code userId} is the target whose profile set must
 * be reconciled; {@code refId} carries the originating entity (employee id,
 * team relation id, project assignment id, …) for audit traceability.
 */
public record StructuralChangeEvent(
    StructuralEventType type,
    UUID userId,
    UUID refId,
    UUID actorId
) {
    public static StructuralChangeEvent of(StructuralEventType type, UUID userId, UUID refId, UUID actorId) {
        return new StructuralChangeEvent(type, userId, refId, actorId);
    }
}
