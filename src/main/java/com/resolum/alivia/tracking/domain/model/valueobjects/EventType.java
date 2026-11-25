package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.util.EnumSet;
import java.util.Set;

/**
 * What happened. Each type says who may report it, what it needs and how much it matters.
 *
 * <p>The edges report most of them; the connection ones are only produced by the server, which is the only one that can
 * tell that an edge went silent.</p>
 */
public enum EventType {
    COMMAND_REQUESTED(true, true, true, true, Set.of()),
    COMMAND_RESULT(true, true, true, true,
            EnumSet.of(EventResult.SUCCESS, EventResult.FAILED, EventResult.FAILED_TIMEOUT, EventResult.NO_CONFIRMATION)),
    COMMAND_REJECTED(true, false, true, false,
            EnumSet.of(EventResult.REJECTED_LOW_CONFIDENCE, EventResult.REJECTED_UNAUTHORIZED)),
    DEVICE_STATE_CHANGED(true, true, false, false, Set.of()),
    DEVICE_RECOVERED(true, true, false, false, Set.of()),
    LOW_BATTERY(true, false, false, false, Set.of()),
    DEVICE_FAILURE(true, false, false, false, Set.of()),
    HELP_REQUESTED(true, false, false, false, Set.of()),
    EDGE_DISCONNECTED(false, false, false, false, Set.of()),
    EDGE_RECONNECTED(false, false, false, false, Set.of()),
    DEVICE_DISCONNECTED(false, true, false, false, Set.of());

    private final boolean fromEdge;
    private final boolean deviceRequired;
    private final boolean commandRelated;
    private final boolean actionRequired;
    private final Set<EventResult> results;

    EventType(boolean fromEdge, boolean deviceRequired, boolean commandRelated, boolean actionRequired,
              Set<EventResult> results) {
        this.fromEdge = fromEdge;
        this.deviceRequired = deviceRequired;
        this.commandRelated = commandRelated;
        this.actionRequired = actionRequired;
        this.results = results;
    }

    /**
     * Whether the family is told at once: a request for help, a device that failed or a battery that runs out. Those
     * reports are never refused for what they say about a device or about the clock of the edge.
     */
    public boolean isUrgent() {
        return this == HELP_REQUESTED || this == DEVICE_FAILURE || this == LOW_BATTERY;
    }

    /** Whether an edge may report it. */
    public boolean reportedByEdge() {
        return fromEdge;
    }

    public boolean requiresDevice() {
        return deviceRequired;
    }

    /** Whether it belongs to a command: it carries the {@code commandId} that joins request, result and rejection. */
    public boolean isCommandRelated() {
        return commandRelated;
    }

    public boolean requiresAction() {
        return actionRequired;
    }

    /** Whether it carries a result: those of {@link #allowedResults()}. */
    public boolean hasResult() {
        return !results.isEmpty();
    }

    public Set<EventResult> allowedResults() {
        return results;
    }

    /** The importance of an event of this type that ended with the result ({@code null} when it has none). */
    public EventSeverity severity(EventResult result) {
        return switch (this) {
            case COMMAND_RESULT -> result == EventResult.SUCCESS ? EventSeverity.INFORMATIVE : EventSeverity.IMPORTANT;
            case COMMAND_REJECTED -> result == EventResult.REJECTED_UNAUTHORIZED
                    ? EventSeverity.IMPORTANT : EventSeverity.INFORMATIVE;
            case COMMAND_REQUESTED, DEVICE_STATE_CHANGED, DEVICE_RECOVERED, EDGE_RECONNECTED -> EventSeverity.INFORMATIVE;
            case LOW_BATTERY -> EventSeverity.PREVENTIVE;
            case DEVICE_FAILURE, EDGE_DISCONNECTED, DEVICE_DISCONNECTED -> EventSeverity.IMPORTANT;
            case HELP_REQUESTED -> EventSeverity.CRITICAL;
        };
    }
}
