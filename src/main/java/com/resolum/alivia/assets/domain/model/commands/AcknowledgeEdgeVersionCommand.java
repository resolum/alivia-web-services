package com.resolum.alivia.assets.domain.model.commands;

/** The edge reports the highest version of the table of devices that it applied. */
public record AcknowledgeEdgeVersionCommand(Long edgeId, Long appliedVersion) {

    public AcknowledgeEdgeVersionCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (appliedVersion == null || appliedVersion < 0) {
            throw new IllegalArgumentException("Applied version cannot be null or negative.");
        }
    }
}
