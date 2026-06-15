package com.hris.recruitment.enums;

/**
 * Lifecycle of a recruitment requisition (an open role).
 *
 * <p>DRAFT &rarr; PENDING_APPROVAL (submit) &rarr; OPEN (approved) | back to DRAFT (rejected).
 * OPEN &harr; ON_HOLD. OPEN &rarr; FILLED (auto when filled_count == headcount) | CLOSED (manual).
 * Any non-terminal status &rarr; CANCELLED.
 */
public enum RequisitionStatus {
    DRAFT,
    PENDING_APPROVAL,
    OPEN,
    ON_HOLD,
    FILLED,
    CLOSED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FILLED || this == CLOSED || this == CANCELLED;
    }

    /** A requisition can accept new hires while it is actively recruiting. */
    public boolean acceptsHires() {
        return this == OPEN || this == ON_HOLD;
    }
}
