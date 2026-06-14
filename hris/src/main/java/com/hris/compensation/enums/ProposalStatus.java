package com.hris.compensation.enums;

/**
 * Per-employee proposal lifecycle within a comp-review cycle. PENDING (generated,
 * awaiting a manager) -> PROPOSED (manager entered an increase) -> APPROVED /
 * REJECTED (HR) -> APPLIED (written as a compensation record on apply).
 */
public enum ProposalStatus {
    PENDING, PROPOSED, APPROVED, REJECTED, APPLIED
}
