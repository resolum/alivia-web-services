package com.resolum.alivia.business.domain.exceptions;

public class AdministratorNotFound extends RuntimeException {
    public AdministratorNotFound() {
        super("Administrator not found");
    }

    public AdministratorNotFound(Long administratorId) {
        super("Administrator with id " + administratorId + " not found");
    }
}
