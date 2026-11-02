package com.resolum.alivia.profiles.domain.exceptions;

import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;

public class LegalNameAlreadyExistsException extends RuntimeException {
    public LegalNameAlreadyExistsException(LegalName legalName) {
        this(legalName.name());
    }

    public LegalNameAlreadyExistsException(String legalName) {
        super("Legal name " + legalName + " already exists");
    }
}
