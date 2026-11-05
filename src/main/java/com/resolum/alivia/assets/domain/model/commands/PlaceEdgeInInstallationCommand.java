package com.resolum.alivia.assets.domain.model.commands;

/**
 * Puts an edge in an installation. It takes the place of its type (microphone or actuators); the edge that was there
 * leaves the installation.
 */
public record PlaceEdgeInInstallationCommand(Long installationId, Long edgeId) {

    public PlaceEdgeInInstallationCommand {
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
