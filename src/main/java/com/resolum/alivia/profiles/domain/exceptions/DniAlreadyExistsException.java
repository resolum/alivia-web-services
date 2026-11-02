package com.resolum.alivia.profiles.domain.exceptions;

import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;

public class DniAlreadyExistsException extends RuntimeException {
    public DniAlreadyExistsException(Dni dni) {
        this(dni.dni());
    }

    public DniAlreadyExistsException(String dni) {
        super("DNI " + dni + " already exists");
    }
}
