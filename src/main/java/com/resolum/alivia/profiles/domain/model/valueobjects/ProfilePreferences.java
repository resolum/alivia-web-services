package com.resolum.alivia.profiles.domain.model.valueobjects;

import java.util.Map;

/**
 * Communication preferences of a profile.
 *
 * <p>US-14 asks for communication preferences without fixing their keys, so the value object
 * keeps them as an immutable map. Persistence stores the map as JSONB.</p>
 *
 * @param values the preference values, keyed by preference name
 */
public record ProfilePreferences(Map<String, Object> values) {

    public ProfilePreferences {
        if (values == null) {
            throw new IllegalArgumentException("Preferences cannot be null.");
        }
        if (values.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("Preferences cannot contain null keys or values.");
        }
        values = Map.copyOf(values);
    }

    /**
     * Returns an empty set of preferences.
     *
     * @return the empty preferences
     */
    public static ProfilePreferences empty() {
        return new ProfilePreferences(Map.of());
    }
}
