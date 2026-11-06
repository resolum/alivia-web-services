package com.resolum.alivia.assets.domain.model.valueobjects;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;

/**
 * An edge of the inventory as the business reads it.
 *
 * @param installationId installation where the edge is assigned now, or {@code null} when it is not assigned
 */
public record EdgeInventoryItem(Edge edge, Long installationId) {
}
