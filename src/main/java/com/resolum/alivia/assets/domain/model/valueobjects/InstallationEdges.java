package com.resolum.alivia.assets.domain.model.valueobjects;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;

/**
 * The edge services that run the devices of an installation.
 *
 * @param installationId   installation
 * @param microphoneEdgeId edge that listens to the microphones, or {@code null} when none is assigned
 * @param actuatorEdgeId   edge that runs the actuators, or {@code null} when none is assigned
 * @param replacedEdgeId   edge that left the installation because of this change, or {@code null}
 */
public record InstallationEdges(Long installationId, Long microphoneEdgeId, Long actuatorEdgeId, Long replacedEdgeId) {

    public static InstallationEdges of(HouseholdDeviceRegistry registry, Edge replaced) {
        return new InstallationEdges(registry.getInstallationId().installationId(), idOf(registry.getMicrophoneEdge()),
                idOf(registry.getActuatorEdge()), idOf(replaced));
    }

    private static Long idOf(Edge edge) {
        return edge == null ? null : edge.getId();
    }
}
