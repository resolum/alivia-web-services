package com.resolum.alivia.nursing.domain.exceptions;

/**
 * Thrown when an assignment does not belong to the care activity.
 */
public class ActivityAssignmentNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param assignmentId the requested assignment identifier
     */
    public ActivityAssignmentNotFoundException(Long assignmentId) {
        super("Activity assignment " + assignmentId + " was not found.");
    }
}
