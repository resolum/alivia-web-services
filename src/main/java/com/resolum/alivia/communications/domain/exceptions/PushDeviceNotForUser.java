package com.resolum.alivia.communications.domain.exceptions;

public class PushDeviceNotForUser extends RuntimeException {
    public PushDeviceNotForUser(String token) {
        super("Push device with token " + token + " does not belong to this user.");
    }
}
