package com.resolum.alivia.tracking.domain.model.valueobjects;

/** How a command ended, or why it was not run. */
public enum EventResult {
    SUCCESS,
    FAILED,
    FAILED_TIMEOUT,
    /** The voice edge got no answer from the actuator before its deadline. */
    NO_CONFIRMATION,
    REJECTED_LOW_CONFIDENCE,
    REJECTED_UNAUTHORIZED
}
