package com.resolum.alivia.business.domain.exceptions;

public class UnauthorizedAdministratorException extends RuntimeException {
    public UnauthorizedAdministratorException() {
        super("Administrator is not authorized to manage this business");
    }

    public UnauthorizedAdministratorException(String message) {
        super(message);
    }
}
