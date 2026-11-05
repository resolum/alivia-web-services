package com.resolum.alivia.assets.domain.exceptions;

/** Extends {@link IllegalArgumentException} so it is still answered as a 400 by the global handler. */
public class InvalidMacAddress extends IllegalArgumentException {
    public InvalidMacAddress(String message) {
        super(message);
    }
}
