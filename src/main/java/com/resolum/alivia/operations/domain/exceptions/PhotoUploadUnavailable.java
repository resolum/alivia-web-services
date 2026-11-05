package com.resolum.alivia.operations.domain.exceptions;

/** Photo storage is not configured or did not answer; the incident itself is not affected. */
public class PhotoUploadUnavailable extends RuntimeException {
    public PhotoUploadUnavailable() {
        super("Photo upload is not available right now.");
    }
}
