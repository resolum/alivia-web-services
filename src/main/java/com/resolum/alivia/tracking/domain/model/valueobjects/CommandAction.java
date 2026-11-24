package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.util.Locale;
import java.util.Optional;

/** The verbs of the local command contract (MQTT v2) between the voice edge and the actuator edge. */
public enum CommandAction {
    OPEN,
    CLOSE,
    ON,
    OFF;

    /** The verb as the edges write it: {@code open}, {@code close}, {@code on} or {@code off}. */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CommandAction> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
