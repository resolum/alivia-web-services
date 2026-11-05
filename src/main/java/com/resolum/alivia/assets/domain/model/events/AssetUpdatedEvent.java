package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetKind;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;

/**
 * The MAC address or the type of a device or edge changed.
 *
 * @param assetKind  device or edge
 * @param assetId    id of the asset
 * @param macAddress physical identifier after the change
 * @param assetType  device type or edge type name after the change
 * @param status     status of the asset
 */
public record AssetUpdatedEvent(AssetKind assetKind, Long assetId, String macAddress, String assetType,
                                AssetStatus status) {
}
