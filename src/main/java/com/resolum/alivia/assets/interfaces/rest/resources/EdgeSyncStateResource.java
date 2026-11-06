package com.resolum.alivia.assets.interfaces.rest.resources;

import java.time.Instant;

/**
 * What the server stores of the confirmation of an edge: the highest version it confirmed (which may be higher than the
 * one just reported) and when it was confirmed.
 */
public record EdgeSyncStateResource(long appliedVersion, Instant ackedAt) {
}
