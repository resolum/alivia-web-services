package com.resolum.alivia.assets.application.internal.outboundservices.edge;

/**
 * What the edge services of an installation need to know after something changed in the inventory of that
 * installation. Only facts, no free text: the reason of an unassignment stays in the history.
 *
 * @param type           what happened
 * @param installationId installation whose edge services must synchronize
 * @param householdId    household of that installation
 * @param assetKind      whether the asset is a device or an edge
 * @param assetId        id of the asset
 * @param macAddress     physical identifier of the asset (canonical form)
 * @param assetType      device type or edge type name
 * @param status         status of the asset after the change
 * @param roomKey        room of the placement, for assignments
 * @param function       function in the room, for assignments
 */
public record EdgeSyncMessage(Type type, Long installationId, Long householdId, AssetKind assetKind, Long assetId,
                              String macAddress, String assetType, String status, String roomKey, String function) {

    public enum Type {
        DEVICE_ASSIGNED,
        DEVICE_UNASSIGNED,
        ASSET_STATUS_CHANGED,
        ASSET_UPDATED
    }

    public enum AssetKind {
        DEVICE,
        EDGE
    }
}
