package com.hris.compensation.enums;

/**
 * Comp-review cycle lifecycle. DRAFT (config) -> ACTIVE (managers propose) ->
 * IN_REVIEW (manager input locked, HR approves) -> CLOSED (approved proposals
 * applied as compensation records).
 */
public enum ReviewCycleStatus {
    DRAFT, ACTIVE, IN_REVIEW, CLOSED
}
