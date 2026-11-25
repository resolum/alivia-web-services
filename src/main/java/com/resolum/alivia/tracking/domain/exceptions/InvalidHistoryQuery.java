package com.resolum.alivia.tracking.domain.exceptions;

/** The request for a history asks for something that cannot be answered: the reason is a code the client can show. */
public class InvalidHistoryQuery extends RuntimeException {

    private final String code;

    public InvalidHistoryQuery(String code, String message) {
        super(message);
        this.code = code;
    }

    /** {@code invalid_range}, {@code range_too_large}, {@code invalid_page}, {@code invalid_page_size} or {@code invalid_type}. */
    public String code() {
        return code;
    }
}
