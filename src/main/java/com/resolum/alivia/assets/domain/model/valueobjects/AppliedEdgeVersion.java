package com.resolum.alivia.assets.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * What the server keeps of the confirmation of an edge after it reported a version: the highest version confirmed and
 * when it was confirmed (UTC).
 */
public record AppliedEdgeVersion(Long edgeId, long appliedVersion, LocalDateTime ackedAt) {
}
