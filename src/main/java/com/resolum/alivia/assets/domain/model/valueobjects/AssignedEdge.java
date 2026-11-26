package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * An edge service assigned to an installation.
 *
 * @param edgeId   the edge
 * @param edgeType whether it is the microphone or the actuators edge of the installation
 */
public record AssignedEdge(Long edgeId, EdgeType edgeType) {
}
