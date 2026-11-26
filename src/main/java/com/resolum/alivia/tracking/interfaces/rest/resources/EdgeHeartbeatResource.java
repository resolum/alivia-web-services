package com.resolum.alivia.tracking.interfaces.rest.resources;

/**
 * What the edge says about itself.
 *
 * @param pendingEvents how many events it still has to send
 * @param uptimeS       how long it has been running
 */
public record EdgeHeartbeatResource(Integer pendingEvents, Long uptimeS) {
}
