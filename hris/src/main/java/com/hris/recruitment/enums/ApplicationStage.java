package com.hris.recruitment.enums;

/**
 * Pipeline stage of a candidate's application to a requisition.
 *
 * <p>APPLIED &rarr; SCREENING &rarr; INTERVIEW &rarr; OFFER &rarr; HIRED. Any non-terminal stage
 * may move to REJECTED or WITHDRAWN. Recruiters may move forward or backward between
 * non-terminal stages.
 */
public enum ApplicationStage {
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    HIRED,
    REJECTED,
    WITHDRAWN;

    public boolean isTerminal() {
        return this == HIRED || this == REJECTED || this == WITHDRAWN;
    }
}
