package com.resolum.alivia.assets.domain.model.queries;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import org.springframework.data.domain.Pageable;

/**
 * A page of the edges in the inventory of a business, optionally of one status and one type.
 *
 * @param status   only the edges in this status; {@code null} for any
 * @param edgeType only the edges of this type; {@code null} for any
 */
public record GetEdgesQuery(Long businessId, AssetStatus status, EdgeType edgeType, Pageable pageable) {
}
