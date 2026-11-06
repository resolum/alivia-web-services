package com.resolum.alivia.assets.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

/**
 * How far the edge services of an installation are from the current table of devices.
 *
 * @param currentVersion current version of the table
 */
public record InstallationEdgeSyncResource(Long installationId, long currentVersion, List<Edge> edges) {

    /**
     * @param appliedVersion highest version the edge confirmed; {@code null} if it never confirmed one
     * @param ackedAt        when it confirmed it
     * @param lastSeenAt     last time it asked for its table; {@code null} if it never did
     * @param behindBy       versions behind the current table; {@code null} if it never confirmed one
     * @param upToDate       whether it confirmed the current version
     */
    public record Edge(Long edgeId, String edgeType, Long appliedVersion, Instant ackedAt, Instant lastSeenAt,
                       Long behindBy, boolean upToDate) {
    }
}
