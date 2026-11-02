package com.resolum.alivia.profiles.domain.exceptions;

import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;

public class RucAlreadyExistsException extends RuntimeException {
    public RucAlreadyExistsException(Ruc ruc) {
        this(ruc.ruc());
    }

    public RucAlreadyExistsException(String ruc) {
        super("RUC " + ruc + " already exists");
    }
}
