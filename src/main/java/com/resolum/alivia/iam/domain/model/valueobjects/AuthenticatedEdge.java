package com.resolum.alivia.iam.domain.model.valueobjects;

/**
 * The edge service that a valid credential identified.
 *
 * @param edgeId   inventory id of the edge
 * @param edgeKind kind of the edge
 */
public record AuthenticatedEdge(Long edgeId, EdgeKind edgeKind) {
}
