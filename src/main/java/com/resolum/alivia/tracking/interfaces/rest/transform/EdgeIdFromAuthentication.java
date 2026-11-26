package com.resolum.alivia.tracking.interfaces.rest.transform;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/** Reads the edge that calls from its authentication. */
public final class EdgeIdFromAuthentication {

    private static final String EDGE_PREFIX = "edge:";

    private EdgeIdFromAuthentication() {
    }

    /**
     * IAM names an authenticated edge service {@code edge:<id>}, with the id of the edge in the inventory. Anything else
     * is not an edge service and is refused.
     */
    public static Long of(Authentication authentication) {
        var name = authentication == null ? null : authentication.getName();
        if (name != null && name.startsWith(EDGE_PREFIX)) {
            try {
                var edgeId = Long.parseLong(name.substring(EDGE_PREFIX.length()));
                if (edgeId > 0) return edgeId;
            } catch (NumberFormatException ignored) {
                // falls through
            }
        }
        throw new AccessDeniedException("The caller is not an edge service.");
    }
}
