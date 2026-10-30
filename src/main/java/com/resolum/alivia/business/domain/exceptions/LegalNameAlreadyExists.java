package com.resolum.alivia.business.domain.exceptions;

public class LegalNameAlreadyExists extends RuntimeException {
    public LegalNameAlreadyExists() {
        super("Legal name already exists");
    }

    public LegalNameAlreadyExists(String legalName) {
        super("Legal name " + legalName + " already exists");
    }
}
