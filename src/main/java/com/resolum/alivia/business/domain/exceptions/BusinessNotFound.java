package com.resolum.alivia.business.domain.exceptions;

public class BusinessNotFound extends RuntimeException {
    public BusinessNotFound() {
        super("Business not found");
    }

    public BusinessNotFound(Long businessId) {
        super("Business with id " + businessId + " not found");
    }
}
