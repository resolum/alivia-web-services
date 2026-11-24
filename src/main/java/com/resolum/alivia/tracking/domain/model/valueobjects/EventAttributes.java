package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.exceptions.InvalidTrackingData;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The free part of an event (or the diagnostics of a device): a small map of scalar values for what varies from one
 * device to another, such as {@code state} or {@code elapsedMs}.
 *
 * <p>It is bounded in keys and size, and refuses the keys that would carry what must never be stored: audio and
 * transcriptions (RN-10).</p>
 */
public record EventAttributes(Map<String, Object> values) {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,40}");
    private static final int MAX_TEXT = 256;
    /** Keys, in lower case, that name audio or what was said. */
    private static final Set<String> FORBIDDEN = Set.of("audio", "pcm", "wav", "samples", "transcript");

    public EventAttributes {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static EventAttributes empty() {
        return new EventAttributes(Map.of());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * @throws InvalidTrackingData {@code attributes_invalid} or {@code forbidden_attribute}
     */
    public static EventAttributes of(Map<String, ?> raw, TrackingPolicy policy) {
        if (raw == null || raw.isEmpty()) return empty();
        if (raw.size() > policy.maxAttributeKeys()) {
            throw new InvalidTrackingData(RejectionReason.ATTRIBUTES_INVALID, "Too many attributes.");
        }
        var copy = new LinkedHashMap<String, Object>();
        long bytes = 0;
        for (var entry : raw.entrySet()) {
            var key = entry.getKey();
            if (key == null || !KEY.matcher(key).matches()) {
                throw new InvalidTrackingData(RejectionReason.ATTRIBUTES_INVALID, "Invalid attribute name.");
            }
            if (FORBIDDEN.contains(key.toLowerCase(Locale.ROOT))) {
                throw new InvalidTrackingData(RejectionReason.FORBIDDEN_ATTRIBUTE,
                        "The attribute " + key + " may not be reported.");
            }
            var value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new InvalidTrackingData(RejectionReason.ATTRIBUTES_INVALID,
                        "Attribute values must be text, numbers or booleans.");
            }
            if (value instanceof String text && text.length() > MAX_TEXT) {
                throw new InvalidTrackingData(RejectionReason.ATTRIBUTES_INVALID, "An attribute text is too long.");
            }
            bytes += key.getBytes(StandardCharsets.UTF_8).length + String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > policy.maxAttributeBytes()) {
                throw new InvalidTrackingData(RejectionReason.ATTRIBUTES_INVALID, "The attributes are too big.");
            }
            copy.put(key, value);
        }
        return new EventAttributes(copy);
    }
}
