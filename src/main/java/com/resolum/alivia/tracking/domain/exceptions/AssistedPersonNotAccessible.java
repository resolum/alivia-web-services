package com.resolum.alivia.tracking.domain.exceptions;

/**
 * The assisted person does not exist or the user may not read it. Both cases are the same exception on purpose: the
 * answer must not tell a user which people exist in a household that is not theirs.
 */
public class AssistedPersonNotAccessible extends RuntimeException {
    public AssistedPersonNotAccessible(Long assistedPersonId) {
        super("The assisted person " + assistedPersonId + " was not found.");
    }
}
