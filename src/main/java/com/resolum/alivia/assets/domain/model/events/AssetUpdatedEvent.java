package com.resolum.alivia.assets.domain.model.events;

import com.resolum.alivia.assets.domain.model.aggregates.Asset;

/** The MAC address or the type of a device or edge changed. */
public record AssetUpdatedEvent(Asset asset) {
}
