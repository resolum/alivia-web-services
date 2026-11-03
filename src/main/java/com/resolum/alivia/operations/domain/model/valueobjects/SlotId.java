package com.resolum.alivia.operations.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier of a bookable block: its date and window, written as {@code 2026-11-05T08:00-10:00}.
 *
 * <p>Blocks are computed from the technicians' schedules and are not stored, so the id carries the data
 * needed to find the block again when the order is created.</p>
 */
public record SlotId(LocalDate date, TimeRange window) {
    private static final Pattern FORMAT = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2})-(\\d{2}:\\d{2})$");

    public SlotId {
        Objects.requireNonNull(date, "The block date is required.");
        Objects.requireNonNull(window, "The block window is required.");
        if (window.crossesMidnight()) throw new IllegalArgumentException("A block must end within its day.");
    }

    public static SlotId parse(String value) {
        var matcher = value == null ? null : FORMAT.matcher(value.trim());
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("The block id must look like 2026-11-05T08:00-10:00.");
        }
        try {
            return new SlotId(LocalDate.parse(matcher.group(1)),
                    new TimeRange(LocalTime.parse(matcher.group(2)), LocalTime.parse(matcher.group(3))));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("The block id has an invalid date or time.");
        }
    }

    public String value() {
        return date + "T" + window.start() + "-" + window.end();
    }

    @Override
    public String toString() {
        return value();
    }
}
