package com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.operations.domain.model.aggregates.EvaluationSlotHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EvaluationSlotHoldRepository extends JpaRepository<EvaluationSlotHold, Long> {
    Optional<EvaluationSlotHold> findByDraftId(String draftId);

    List<EvaluationSlotHold> findByScheduledDateAndExpiresAtAfter(LocalDate scheduledDate, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EvaluationSlotHold hold where hold.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EvaluationSlotHold hold where hold.draftId = :draftId")
    int deleteByDraft(@Param("draftId") String draftId);
}
