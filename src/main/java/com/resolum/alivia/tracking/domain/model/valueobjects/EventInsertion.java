package com.resolum.alivia.tracking.domain.model.valueobjects;

/** What keeping an event did: it was new, or the edge had already reported it. */
public enum EventInsertion {
    INSERTED,
    DUPLICATE
}
