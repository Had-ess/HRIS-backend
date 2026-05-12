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
     * Used as actorId for scheduled jobs and RabbitMQ consumers that operate without a human user.
     */
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static final String SYSTEM_ACTOR_NAME = "SYSTEM";

    public static boolean isSystemActor(UUID actorId) {
        return SYSTEM_ACTOR_ID.equals(actorId);
    }
}
