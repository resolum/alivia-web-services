package com.resolum.alivia.assets.domain.model.commands;

/** The edge asked for its table: it was seen alive. */
public record RecordEdgeSeenCommand(Long edgeId, Long installationId) {

    public RecordEdgeSeenCommand {
        if (edgeId == null || edgeId <= 0) {
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
    }
}
