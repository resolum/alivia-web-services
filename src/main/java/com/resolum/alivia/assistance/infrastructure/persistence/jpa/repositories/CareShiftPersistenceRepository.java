package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CareShiftPersistenceRepository extends JpaRepository<CareShift, Long> {

    @Query("SELECT s FROM CareShift s WHERE s.assistedPerson.id = :assistedPersonId AND s.startsAt < :to AND s.endsAt > :from AND s.status != 'CANCELLED' ORDER BY s.startsAt ASC")
    List<CareShift> findByAssistedPersonIdAndRange(
            @Param("assistedPersonId") Long assistedPersonId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT s FROM CareShift s WHERE s.assistedPerson.id = :assistedPersonId AND s.status != 'CANCELLED' ORDER BY s.startsAt ASC")
    List<CareShift> findByAssistedPersonId(@Param("assistedPersonId") Long assistedPersonId);

    @Query("SELECT s FROM CareShift s WHERE s.assistedPerson.id = :assistedPersonId AND s.startsAt < :to AND s.endsAt > :from AND s.status != 'CANCELLED' AND (:excludeShiftId IS NULL OR s.id != :excludeShiftId)")
    List<CareShift> findConflictingShifts(
            @Param("assistedPersonId") Long assistedPersonId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("excludeShiftId") Long excludeShiftId
    );
}
