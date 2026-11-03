package com.resolum.alivia.business.domain.exceptions;

public class RucAlreadyExists extends RuntimeException {
    public RucAlreadyExists() {
        super("RUC already exists");
    }

    public RucAlreadyExists(Long ruc) {
        super("RUC " + ruc + " already exists");
    }

    public RucAlreadyExists(String ruc) {
        super("RUC " + ruc + " already exists");
    }
}
