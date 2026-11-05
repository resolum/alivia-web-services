package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * Life-cycle status of a device or edge.
 *
 * <p>{@code IN_USE} is the status of an asset assigned to an installation (TS-10 calls it
 * {@code ASSIGNED}). {@code RETIRED} is terminal: a retired asset can no longer change status.
 * An asset in use must be unassigned (back to {@code AVAILABLE}) before it can be retired, so
 * the assignment history is closed first.</p>
 */
public enum AssetStatus {
    AVAILABLE,
    IN_USE,
    MAINTENANCE,
    BROKEN,
    RETIRED;

    /** Whether an asset in this status may move to {@code target}. */
    public boolean canChangeTo(AssetStatus target) {
        if (target == null) return false;
        return switch (this) {
            case AVAILABLE -> target == IN_USE || target == MAINTENANCE || target == BROKEN || target == RETIRED;
            case IN_USE -> target == AVAILABLE || target == MAINTENANCE || target == BROKEN;
            case MAINTENANCE -> target == AVAILABLE || target == BROKEN || target == RETIRED;
            case BROKEN -> target == MAINTENANCE || target == RETIRED;
            case RETIRED -> false;
        };
    }
}
