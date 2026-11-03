package com.resolum.alivia.business.domain.exceptions;

public class DniAlreadyExists extends RuntimeException {
    public DniAlreadyExists() {
        super("DNI already exists");
    }

    public DniAlreadyExists(String dni) {
        super("DNI " + dni + " already exists");
    }
}
