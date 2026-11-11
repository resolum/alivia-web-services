package com.resolum.alivia.assistance.domain.exceptions;

public class HandoverNotPendingException extends RuntimeException {
    public HandoverNotPendingException(Long handoverId) {
        super("Handover " + handoverId + " is no longer in pending status.");
    }
}
