package com.hris.common.event;

import java.util.UUID;

/**
 * Well-known constants for the system/scheduler actor.
 * This is not a real user and must never appear in profile/menu assignments.
 */
public final class SystemActor {

    private SystemActor() {}

    /**
     * Fixed UUID representing the system actor.
     * Used as actorId for scheduled jobs and event processors that operate without a human user.
     */
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static final String SYSTEM_ACTOR_NAME = "SYSTEM";

    public static boolean isSystemActor(UUID actorId) {
        return SYSTEM_ACTOR_ID.equals(actorId);
    }

    /**
     * Resolves an actor id for storage in a column that has a FK to {@code users}.
     * The system actor is not a real user, so it is represented as {@code null}
     * (every such column is nullable). Use this before persisting audit trails or
     * history rows that may be written by a scheduled job — otherwise the FK
     * rejects the all-zeros sentinel and rolls back the whole transaction.
     */
    public static UUID toUserReference(UUID actorId) {
        return isSystemActor(actorId) ? null : actorId;
    }
}
