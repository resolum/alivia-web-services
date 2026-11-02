package com.resolum.alivia.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Secure reference of a photo the storage service already confirmed. */
public record AttachEvidenceResource(@NotBlank @Size(max = 500) String url, @NotBlank @Size(max = 200) String publicId) {
}
