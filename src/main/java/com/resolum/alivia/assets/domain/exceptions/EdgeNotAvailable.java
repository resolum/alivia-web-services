package com.resolum.alivia.assets.domain.exceptions;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/** The edge cannot be placed in an installation because it is not {@code AVAILABLE} (in use, in maintenance, broken or retired). */
public class EdgeNotAvailable extends RuntimeException {
    private final AssetStatus status;

    public EdgeNotAvailable(Long edgeId, AssetStatus status) {
        super(String.format("edge with id %d is %s and cannot be placed in an installation", edgeId, status));
        this.status = status;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public boolean isAlreadyAssigned() {
        return status == AssetStatus.IN_USE;
    }
}
