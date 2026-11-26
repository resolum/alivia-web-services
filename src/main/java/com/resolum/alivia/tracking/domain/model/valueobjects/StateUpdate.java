package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;

/**
 * The answer to a state report.
 *
 * @param outcome     what happened
 * @param reason      why it was rejected, or {@code null}
 * @param state       the state to keep: the new one when applied, the one already kept otherwise ({@code null} if none)
 * @param diagnostics the diagnostics of the report, to keep as a sample; empty when it was not applied
 */
public record StateUpdate(StateOutcome outcome, RejectionReason reason, DeviceState state, EventAttributes diagnostics) {
}
