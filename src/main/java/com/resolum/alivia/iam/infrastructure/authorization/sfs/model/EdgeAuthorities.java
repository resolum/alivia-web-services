package com.resolum.alivia.iam.infrastructure.authorization.sfs.model;

import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;

/**
 * The authorities an authenticated edge service carries, one per kind of edge. A user's authority is
 * {@code ROLE_<access role>}, so none of these can be held by a user.
 */
public final class EdgeAuthorities {

    public static final String MICROPHONE = "ROLE_EDGE_MICROPHONE";
    public static final String ACTUATOR = "ROLE_EDGE_ACTUATOR";

    private EdgeAuthorities() {
    }

    /** Authority of an edge of the given kind. */
    public static String of(EdgeKind kind) {
        return switch (kind) {
            case MICROPHONE -> MICROPHONE;
            case ACTUATOR -> ACTUATOR;
        };
    }
}
