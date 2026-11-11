package com.resolum.alivia.communications.domain.exceptions;

public class PushDeviceNotFound extends RuntimeException {
    public PushDeviceNotFound(String token) {
        super("Push device not found with token: " + token);
    }
}
