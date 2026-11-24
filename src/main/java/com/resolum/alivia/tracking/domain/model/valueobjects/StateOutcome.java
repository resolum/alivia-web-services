package com.resolum.alivia.tracking.domain.model.valueobjects;

/** What happened to a device state report. */
public enum StateOutcome {
    /** It is now the last reliable state. */
    APPLIED,
    /** It was observed before the state already kept (a late report after an outage): the kept state stays. */
    STALE,
    /** The data is not valid: the last reliable state stays and the anomaly is recorded. */
    REJECTED
}
