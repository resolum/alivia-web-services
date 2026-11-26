package com.resolum.alivia.tracking.domain.exceptions;

import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;

/** What an edge reported is not valid. It carries the reason the edge reads in the answer. */
public class InvalidTrackingData extends RuntimeException {

    private final RejectionReason reason;

    public InvalidTrackingData(RejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RejectionReason reason() {
        return reason;
    }
}
