package com.resolum.alivia.assets.domain.model.commands;

/**
 * Registers the installation done for a household, which opens the registry of the devices of that household.
 *
 * @param installationId installation the registry belongs to
 * @param householdId    household the installation was done for
 * @param businessId     business that owns the devices
 */
public record RegisterInstallationCommand(Long installationId, Long householdId, Long businessId) {

    public RegisterInstallationCommand {
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be a positive number.");
        }
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be a positive number.");
        }
    }
}
