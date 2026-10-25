package com.resolum.alivia.profiles.domain.exceptions;

public class PersonProfileNotFoundException extends RuntimeException {
    public PersonProfileNotFoundException(Long id) {
        super("Person profile with id " + id + " not found");
    }

    public PersonProfileNotFoundException(String message) {
        super(message);
    }
}
