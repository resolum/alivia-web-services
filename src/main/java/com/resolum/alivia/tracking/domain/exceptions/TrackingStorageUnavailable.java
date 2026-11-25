package com.resolum.alivia.tracking.domain.exceptions;

/**
 * Tracking could not reach its storage. The message tells the caller what to do: an edge that was sending data keeps it
 * and sends it again (safe, because what it already sent is recognised); a person who was reading just tries again.
 */
public class TrackingStorageUnavailable extends RuntimeException {

    /** What an edge reads: it could not keep what it sent. */
    public TrackingStorageUnavailable(Throwable cause) {
        super("The events could not be kept now. Send them again.", cause);
    }

    private TrackingStorageUnavailable(String message, Throwable cause) {
        super(message, cause);
    }

    /** What a person or another context reads: the information could not be read. */
    public static TrackingStorageUnavailable forRead(Throwable cause) {
        return new TrackingStorageUnavailable("The information is not available now. Try again in a moment.", cause);
    }
}
