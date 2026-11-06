package com.resolum.alivia.assets.domain.exceptions;

/**
 * The edge says it has a version of the table of its installation that is higher than the current one. Versions only
 * grow, so the database went back in time (restored from an older backup) and the edge has to take the table the server
 * has even though its version is lower.
 */
public class EdgeTableVersionAhead extends RuntimeException {
    public EdgeTableVersionAhead(Long edgeId, long currentVersion) {
        super(String.format("edge with id %d has a newer table than the server (current version %d): request it again "
                + "without If-None-Match and replace the local copy", edgeId, currentVersion));
    }
}
