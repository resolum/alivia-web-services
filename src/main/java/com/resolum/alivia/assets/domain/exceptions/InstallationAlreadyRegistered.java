package com.resolum.alivia.assets.domain.exceptions;

public class InstallationAlreadyRegistered extends RuntimeException {
    private InstallationAlreadyRegistered(String message) {
        super(message);
    }

    public static InstallationAlreadyRegistered installation(Long installationId) {
        return new InstallationAlreadyRegistered(
                String.format("installation with id %d is already registered", installationId));
    }

    public static InstallationAlreadyRegistered activeInHousehold(Long householdId) {
        return new InstallationAlreadyRegistered(
                String.format("household with id %d already has an installation registered", householdId));
    }
}
