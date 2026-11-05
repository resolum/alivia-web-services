package com.resolum.alivia.operations.application.internal.outboundservices.storage;

import com.resolum.alivia.operations.domain.exceptions.PhotoUploadUnavailable;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores the photos a family attaches to an incident through the shared storage service (Cloudinary).
 * Without the CLOUDINARY_* credentials (local development) uploads are off: the report still works and the
 * web hides the photo field.
 */
@Service
public class IncidentPhotoStorage {
    private final StorageService storage;
    private final boolean enabled;

    @Autowired
    public IncidentPhotoStorage(StorageService storage,
                                @Value("${cloudinary.cloud-name:}") String cloudName,
                                @Value("${cloudinary.api-key:}") String apiKey,
                                @Value("${cloudinary.api-secret:}") String apiSecret) {
        this(storage, Stream.of(cloudName, apiKey, apiSecret).allMatch(IncidentPhotoStorage::isConfigured));
    }

    public IncidentPhotoStorage(StorageService storage, boolean enabled) {
        this.storage = storage;
        this.enabled = enabled && storage != null;
    }

    /** A blank value or the placeholder default (CLOUDINARY_CLOUD_NAME...) means the credential is missing. */
    static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !value.startsWith("CLOUDINARY_") && !value.startsWith("${");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Uploads the photo and returns its secure URL and public id; unavailable when storage is off or failing. */
    public StoredPhoto store(byte[] data, String fileName) {
        if (!enabled) throw new PhotoUploadUnavailable();
        Map<String, String> uploaded;
        try {
            uploaded = storage.upload(data, fileName);
        } catch (RuntimeException exception) {
            throw new PhotoUploadUnavailable();
        }
        var url = uploaded == null ? null : uploaded.getOrDefault("url", uploaded.get("secure_url"));
        var publicId = uploaded == null ? null : uploaded.getOrDefault("publicId", uploaded.get("public_id"));
        if (url == null || publicId == null) throw new PhotoUploadUnavailable();
        return new StoredPhoto(url, publicId);
    }

    public record StoredPhoto(String url, String publicId) {
    }
}
