package com.resolum.alivia.nursing.domain.model.valueobjects;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque and stable identifier of a medication administration, that is, one occurrence of
 * an assignment of a {@link CareActivityType#MEDICATION} activity.
 *
 * <p>Occurrences are not persisted, so the identifier encodes the assignment and the date
 * the occurrence starts on, as {@code {assignmentId}-{yyyyMMdd}}. This is the only place
 * that encodes and decodes it.</p>
 *
 * @param assignmentId the assignment identifier
 * @param date         the date the occurrence starts on
 */
public record MedicationAdministrationId(Long assignmentId, LocalDate date) {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,18})-(\\d{8})");

    /**
     * Validates the identifier.
     */
    public MedicationAdministrationId {
        if (assignmentId == null || assignmentId <= 0) {
            throw new IllegalArgumentException("Assignment identifier must be positive.");
        }
        Objects.requireNonNull(date, "Date cannot be null.");
    }

    /**
     * Decodes an identifier.
     *
     * @param value the encoded identifier
     * @return the decoded identifier
     * @throws IllegalArgumentException when the value is malformed
     */
    public static MedicationAdministrationId parse(String value) {
        var matcher = value == null ? null : FORMAT.matcher(value);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Malformed medication administration identifier: " + value + ".");
        }
        try {
            return new MedicationAdministrationId(Long.valueOf(matcher.group(1)),
                    LocalDate.parse(matcher.group(2), DateTimeFormatter.BASIC_ISO_DATE));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Malformed medication administration identifier: " + value + ".");
        }
    }

    /**
     * Encodes the identifier.
     *
     * @return the identifier as {@code {assignmentId}-{yyyyMMdd}}
     */
    @Override
    public String toString() {
        return assignmentId + "-" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
