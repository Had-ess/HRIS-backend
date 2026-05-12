package com.hris.common.event;

/**
 * Distinguishes human users from system/scheduler actors in audit trails and events.
 */
public enum ActorType {
    USER,
    SYSTEM
}
