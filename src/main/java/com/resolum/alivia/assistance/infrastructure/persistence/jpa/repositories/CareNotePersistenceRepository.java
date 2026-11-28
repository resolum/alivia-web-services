package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CareNotePersistenceRepository extends JpaRepository<CareNote, Long> {
    List<CareNote> findAllByAssistedPersonIdOrderByCreatedAtDesc(Long assistedPersonId);

    List<CareNote> findAllByAssistedPersonIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long assistedPersonId, LocalDateTime from, LocalDateTime to);
}
