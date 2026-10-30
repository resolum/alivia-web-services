package com.resolum.alivia.business.domain.exceptions;

public class BusinessNameAlreadyExists extends RuntimeException {
    public BusinessNameAlreadyExists() {
        super("Business name already exists");
    }

    public BusinessNameAlreadyExists(String businessName) {
        super("Business name " + businessName + " already exists");
    }
}
