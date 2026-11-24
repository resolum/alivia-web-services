package com.resolum.alivia.assistance.domain.exceptions;

/**
 * The authenticated user is not part of the care network of the assisted person, or has no permission for the
 * operation requested on it.
 */
public class AssistanceAccessDeniedException extends RuntimeException {
    public AssistanceAccessDeniedException(String message) {
        super(message);
    }
}
