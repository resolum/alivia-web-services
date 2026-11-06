package com.resolum.alivia.assets.domain.model.commands;

/** Takes an edge out of an installation. The edge goes back to the inventory and loses its credential. */
public record RemoveEdgeFromInstallationCommand(Long installationId, Long edgeId) {

    public RemoveEdgeFromInstallationCommand {
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }
}
