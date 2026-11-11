package com.resolum.alivia.assets.domain.model.valueobjects;

/** Why the credential of an edge was revoked; the name is what is recorded. */
public enum EdgeCredentialRevocationReason {
    /** An administrator asked for it. */
    REVOKED_BY_ADMINISTRATOR,
    /** The edge was taken out of its installation. */
    EDGE_REMOVED_FROM_INSTALLATION,
    /** Another edge took its place in the installation. */
    EDGE_REPLACED_IN_INSTALLATION,
    /** The edge was retired. */
    EDGE_RETIRED,
    /** The edge changed its type, so the credential of the old kind must not keep working. */
    EDGE_TYPE_CHANGED
}
