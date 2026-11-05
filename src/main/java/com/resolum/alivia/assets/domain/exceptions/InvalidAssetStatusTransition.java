package com.resolum.alivia.assets.domain.exceptions;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

public class InvalidAssetStatusTransition extends RuntimeException {
    public InvalidAssetStatusTransition(AssetStatus from, AssetStatus to) {
        super(String.format("asset cannot change status from %s to %s", from, to));
    }
}
