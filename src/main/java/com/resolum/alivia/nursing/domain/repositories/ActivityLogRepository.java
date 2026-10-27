package com.resolum.alivia.nursing.domain.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link ActivityLog} aggregates.
 */
public interface ActivityLogRepository {

    /**
     * Finds the logs of the given assignments whose occurrence date is within the range.
     *
     * @param assignmentIds the assignment identifiers
     * @param from          the first date, inclusive
     * @param to            the last date, inclusive
     * @return the matching logs
     */
    List<ActivityLog> findAllByAssignmentIdsBetween(Collection<Long> assignmentIds, LocalDate from, LocalDate to);

    /**
     * Finds the log of one occurrence.
     *
     * @param assignmentId the assignment identifier
     * @param date         the date the occurrence starts on
     * @return the log, or empty when nothing was recorded
     */
    Optional<ActivityLog> findByAssignmentIdAndDate(Long assignmentId, LocalDate date);

    /**
     * Inserts a new log and flushes it, so a duplicate occurrence fails here.
     *
     * @param log the new log
     * @return the saved log
     * @throws org.springframework.dao.DataIntegrityViolationException when the occurrence already has a log
     */
    ActivityLog save(ActivityLog log);
}
