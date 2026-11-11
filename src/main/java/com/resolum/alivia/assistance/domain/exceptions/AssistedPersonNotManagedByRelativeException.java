package com.resolum.alivia.assistance.domain.exceptions;

public class AssistedPersonNotManagedByRelativeException extends RuntimeException {
    public AssistedPersonNotManagedByRelativeException(Long assistedPersonId, Long relativeId) {
        super("Assisted person " + assistedPersonId + " is not managed by relative " + relativeId);
    }
}
