package com.resolum.alivia.operations.domain.model.commands;

/** Attaches a photo already stored by the storage service; only the reporting account can add it. */
public record AttachEvidenceCommand(Long incidentId, Long userId, String url, String publicId) {
}
