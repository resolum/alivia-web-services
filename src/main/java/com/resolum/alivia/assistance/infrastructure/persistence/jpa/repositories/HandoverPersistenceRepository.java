package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HandoverPersistenceRepository extends JpaRepository<Handover, Long> {

    @Query("SELECT h FROM Handover h WHERE h.fromShift.assistedPerson.id = :assistedPersonId ORDER BY h.requestedAt DESC")
    List<Handover> findByAssistedPersonId(@Param("assistedPersonId") Long assistedPersonId);

    @Query("SELECT h FROM Handover h WHERE h.fromShift.id = :fromShiftId")
    Optional<Handover> findByFromShiftId(@Param("fromShiftId") Long fromShiftId);

    @Query("SELECT h FROM Handover h WHERE (h.fromShift.id = :shiftId OR h.toShift.id = :shiftId) AND (h.status = 'PENDING' OR h.status = 'DECLINED')")
    List<Handover> findOpenHandoversByShiftId(@Param("shiftId") Long shiftId);
}
