package com.hris.compensation.enums;

/** Why a new compensation record was written (audit + analytics). */
public enum CompensationChangeReason {
    HIRE,
    MERIT,
    PROMOTION,
    MARKET_ADJUSTMENT,
    DEMOTION,
    CORRECTION,
    OTHER
}
