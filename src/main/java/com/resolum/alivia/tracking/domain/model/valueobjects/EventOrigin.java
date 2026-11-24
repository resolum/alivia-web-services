package com.resolum.alivia.tracking.domain.model.valueobjects;

/** Whether an event reached the backend when it happened or after the edge kept it while it had no connection. */
public enum EventOrigin {
    /** Received around the moment it happened. */
    LIVE,
    /** Received late, from the queue of an edge that had no connection: shown as executed locally. */
    SYNCED
}
