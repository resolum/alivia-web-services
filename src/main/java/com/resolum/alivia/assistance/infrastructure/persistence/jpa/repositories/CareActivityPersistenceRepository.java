package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CareActivityPersistenceRepository extends JpaRepository<CareActivity, Long> {
    List<CareActivity> findAllByAssistedPersonId(Long assistedPersonId);

    @Query("SELECT ca FROM CareActivity ca WHERE ca.assistedPerson.relative.id = :relativeId")
    List<CareActivity> findAllByAssistedPersonRelativeId(Long relativeId);

    @Query("SELECT ca FROM CareActivity ca WHERE ca.status = 'ACTIVE'")
    List<CareActivity> findAllActive();
}
