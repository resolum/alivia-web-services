package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.nursing.domain.exceptions.InvalidAgendaRangeException;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Query for the care agenda of the authenticated user within a date range.
 *
 * @param userId                  the authenticated user
 * @param from                    the first date, inclusive
 * @param to                      the last date, inclusive
 * @param assistedPersonProfileId optional assisted person filter
 * @param status                  optional occurrence status filter
 * @param priority                optional priority filter
 */
public record GetCareAgendaQuery(UserId userId, LocalDate from, LocalDate to,
                                 PersonProfileId assistedPersonProfileId,
                                 OccurrenceStatus status, ReminderPriority priority) {

    /**
     * Longest accepted range, in days. Occurrences are computed in memory, so the range is capped.
     */
    public static final long MAX_RANGE_DAYS = 366;

    /**
     * Validates the query.
     *
     * @throws InvalidAgendaRangeException when a date is missing, {@code from} is after {@code to}
     *                                     or the range is longer than {@link #MAX_RANGE_DAYS}
     */
    public GetCareAgendaQuery {
        Objects.requireNonNull(userId, "User cannot be null.");
        if (from == null || to == null) {
            throw new InvalidAgendaRangeException("Both from and to dates are required.");
        }
        if (from.isAfter(to)) {
            throw new InvalidAgendaRangeException("The from date must not be after the to date.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new InvalidAgendaRangeException("The date range cannot exceed " + MAX_RANGE_DAYS + " days.");
        }
    }
}
