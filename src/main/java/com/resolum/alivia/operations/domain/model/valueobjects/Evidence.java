package com.resolum.alivia.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Objects;

/**
 * Photo attached to an incident. The client uploads it to the storage service first and sends the secure
 * reference that the service confirmed; the incident keeps only that reference.
 *
 * @param url      HTTPS address returned by the storage service
 * @param publicId identifier of the file in the storage service, used to delete it later
 */
@Embeddable
public record Evidence(
        @Column(name = "url", nullable = false, length = 500) String url,
        @Column(name = "public_id", nullable = false, length = 200) String publicId,
        @Column(name = "attached_at", nullable = false) Instant attachedAt) {

    public Evidence {
        Objects.requireNonNull(attachedAt, "The attachment time is required.");
        if (publicId == null || publicId.isBlank() || publicId.length() > 200) {
            throw new IllegalArgumentException("The evidence needs the identifier the storage service returned.");
        }
        if (url == null || url.length() > 500 || !isSecure(url.trim())) {
            throw new IllegalArgumentException("The evidence must be a secure (HTTPS) reference of the stored photo.");
        }
        url = url.trim();
        publicId = publicId.trim();
    }

    private static boolean isSecure(String url) {
        try {
            var uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
