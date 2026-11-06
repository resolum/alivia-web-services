package com.resolum.alivia.assets.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.List;

/**
 * How far the edge services of an installation are from the current table of devices.
 *
 * @param currentVersion current version of the table of the installation
 * @param edges          the edge services assigned to the installation (those without a place are absent)
 */
public record InstallationEdgeSync(Long installationId, long currentVersion, List<EdgeSync> edges) {

    public InstallationEdgeSync {
        edges = List.copyOf(edges);
    }

    /**
     * @param appliedVersion highest version the edge confirmed, or {@code null} if it never confirmed one
     * @param ackedAt        when it confirmed it (UTC), or {@code null}
     * @param lastSeenAt     last time the edge asked for its table (UTC), or {@code null} if it never did
     * @param behindBy       how many versions the edge is behind, or {@code null} if it never confirmed one
     * @param upToDate       whether the edge confirmed the current version
     */
    public record EdgeSync(Long edgeId, EdgeType edgeType, Long appliedVersion, LocalDateTime ackedAt,
                           LocalDateTime lastSeenAt, Long behindBy, boolean upToDate) {

        public static EdgeSync of(Long edgeId, EdgeType edgeType, long currentVersion, Long appliedVersion,
                                  LocalDateTime ackedAt, LocalDateTime lastSeenAt) {
            var behindBy = appliedVersion == null ? null : Math.max(0, currentVersion - appliedVersion);
            return new EdgeSync(edgeId, edgeType, appliedVersion, ackedAt, lastSeenAt, behindBy,
                    appliedVersion != null && appliedVersion >= currentVersion);
        }
    }
}
