package com.resolum.alivia.business.domain.exceptions;

public class BusinessAlreadyExistsForAdministrator extends RuntimeException {
    public BusinessAlreadyExistsForAdministrator() {
        super("A business is already managed by this administrator");
    }

    public BusinessAlreadyExistsForAdministrator(Long administratorId) {
        super("Administrator with id " + administratorId + " already manages a business");
    }
}
