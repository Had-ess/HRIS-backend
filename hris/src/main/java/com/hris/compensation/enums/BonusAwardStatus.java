package com.hris.compensation.enums;

/** Lifecycle of a single bonus award (mirrors the merit proposal flow, ending in PAID). */
public enum BonusAwardStatus {
    PENDING,
    PROPOSED,
    APPROVED,
    REJECTED,
    PAID
}
