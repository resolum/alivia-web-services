package com.resolum.alivia.subscriptions.domain.model.valueobjects;

/**
 * Stages a hiring goes through before the service is active, as the commercial team follows them.
 * Subscriptions owns the first one; evaluation and installation will come from Operations.
 */
public enum HiringStage {
    REQUEST_AND_PAYMENT,
    TECHNICAL_EVALUATION,
    INSTALLATION,
    COMPLETED
}
