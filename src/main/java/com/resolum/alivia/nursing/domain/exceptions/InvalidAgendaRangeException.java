package com.resolum.alivia.nursing.domain.exceptions;

/**
 * Thrown when an agenda date range is invalid. It extends {@link IllegalArgumentException}
 * so the shared exception handler answers 400.
 */
public class InvalidAgendaRangeException extends IllegalArgumentException {

    /**
     * Creates the exception.
     *
     * @param message the reason the range is invalid
     */
    public InvalidAgendaRangeException(String message) {
        super(message);
    }
}
