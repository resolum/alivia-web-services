package com.resolum.alivia.assets.domain.model.commands;

/** The technicians of the business confirmed the installation: its network becomes active. */
public record ConfirmInstallationCommand(Long installationId) {

    public ConfirmInstallationCommand {
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
    }
}
