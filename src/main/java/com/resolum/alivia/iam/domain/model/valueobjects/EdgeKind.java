package com.resolum.alivia.iam.domain.model.valueobjects;

import java.util.Locale;

/**
 * The kind of edge service a credential belongs to. Both edge services of a household authenticate with their own
 * credential, and the kind tells them apart.
 */
public enum EdgeKind {
    /** Edge service that listens to the microphones of the household. */
    MICROPHONE,
    /** Edge service that runs the actuators of the household. */
    ACTUATOR;

    /**
     * Reads a kind from its name, ignoring case and surrounding spaces.
     *
     * @param value the name of the kind
     * @return the kind
     * @throws IllegalArgumentException when the value is empty or is not a kind
     */
    public static EdgeKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Edge kind cannot be null or blank.");
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown edge kind: " + value.strip() + ".");
        }
    }
}
