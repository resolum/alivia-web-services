package com.resolum.alivia.tracking.domain.model.valueobjects;

/**
 * What became of one event of a batch.
 *
 * @param eventId the id the edge gave the event, as it sent it
 * @param status  what happened
 * @param reason  why it was rejected; {@code null} otherwise
 */
public record EventOutcome(String eventId, Status status, RejectionReason reason) {

    /** What the edge does with the event: forget it (kept or already kept) or set it apart with the reason. */
    public enum Status {
        /** It is kept now. */
        ACCEPTED,
        /** It was already kept: the edge counts it as synchronized. */
        DUPLICATE,
        /** It is not valid: sending it again changes nothing. */
        REJECTED
    }

    public static EventOutcome accepted(String eventId) {
        return new EventOutcome(eventId, Status.ACCEPTED, null);
    }

    public static EventOutcome duplicate(String eventId) {
        return new EventOutcome(eventId, Status.DUPLICATE, null);
    }

    public static EventOutcome rejected(String eventId, RejectionReason reason) {
        return new EventOutcome(eventId, Status.REJECTED, reason);
    }
}
