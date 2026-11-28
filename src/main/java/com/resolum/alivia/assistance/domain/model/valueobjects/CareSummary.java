package com.resolum.alivia.assistance.domain.model.valueobjects;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What happened in the care of an assisted person in a period, oldest first, so the next caregiver can continue it.
 *
 * @param attended          occurrences of activities and medications that were completed
 * @param pending           occurrences still scheduled or in progress
 * @param missed            occurrences that ended without being done (missed or omitted)
 * @param incompleteSources the sources that could not be read, such as {@code alerts}; empty when the summary is whole
 */
public record CareSummary(Long assistedPersonId, LocalDate from, LocalDate to, int attended, int pending, int missed,
                          List<Entry> entries, List<String> incompleteSources) {

    public enum Kind { ACTIVITY, MEDICATION, ALERT, NOTE }

    /**
     * @param at     when it is scheduled or happened, local time of the service
     * @param status the occurrence status, the severity of an alert or the category of a note
     * @param refId  the activity, the note or the device it is about; {@code null} when there is none
     */
    public record Entry(LocalDateTime at, Kind kind, String title, String status, Long refId, Long authorUserId,
                        String detail) {
    }
}
