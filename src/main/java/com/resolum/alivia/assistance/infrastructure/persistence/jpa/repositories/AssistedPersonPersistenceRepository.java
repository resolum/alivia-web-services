package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistedPersonPersistenceRepository extends JpaRepository<AssistedPerson, Long> {
    List<AssistedPerson> findAllByRelativeId(Long relativeId);
}
