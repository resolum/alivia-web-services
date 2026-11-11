package com.resolum.alivia.assets.interfaces.rest.resources;

/**
 * The edge services of an installation after a change. A place without edge shows {@code null}.
 *
 * @param replacedEdgeId the edge that left the installation because of this change, or {@code null}
 */
public record InstallationEdgesResource(Long installationId, Long microphoneEdgeId, Long actuatorEdgeId,
                                        Long replacedEdgeId) {
}
