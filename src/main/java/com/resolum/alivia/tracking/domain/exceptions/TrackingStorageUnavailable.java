package com.resolum.alivia.tracking.domain.exceptions;

/**
 * The events could not be kept. The edge is not told that they were: it keeps them and sends them again, which is safe
 * because an event it already sent is recognised.
 */
public class TrackingStorageUnavailable extends RuntimeException {
    public TrackingStorageUnavailable(Throwable cause) {
        super("The events could not be kept now. Send them again.", cause);
    }
}
