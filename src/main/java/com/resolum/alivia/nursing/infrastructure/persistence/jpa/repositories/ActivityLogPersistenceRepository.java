package com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link ActivityLog} aggregates.
 */
public interface ActivityLogPersistenceRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Finds the logs of the given assignments whose occurrence date is within the range.
     *
     * @param assignmentIds the assignment identifiers
     * @param from          the first date, inclusive
     * @param to            the last date, inclusive
     * @return the matching logs
     */
    List<ActivityLog> findAllByAssignmentIdInAndDateBetween(Collection<Long> assignmentIds, LocalDate from,
                                                            LocalDate to);

    /**
     * Finds the log of one occurrence.
     *
     * @param assignmentId the assignment identifier
     * @param date         the date the occurrence starts on
     * @return the log, or empty when nothing was recorded
     */
    Optional<ActivityLog> findByAssignmentIdAndDate(Long assignmentId, LocalDate date);
}
