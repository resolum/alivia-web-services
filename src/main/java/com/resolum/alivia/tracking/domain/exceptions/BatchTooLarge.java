package com.resolum.alivia.tracking.domain.exceptions;

/** The edge sent more events in one batch than it may. */
public class BatchTooLarge extends RuntimeException {
    public BatchTooLarge(int size, int max) {
        super("The batch has " + size + " events; the maximum is " + max + ".");
    }
}
