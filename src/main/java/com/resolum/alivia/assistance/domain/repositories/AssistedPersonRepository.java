package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;

import java.util.List;
import java.util.Optional;

public interface AssistedPersonRepository {
    AssistedPerson save(AssistedPerson assistedPerson);
    Optional<AssistedPerson> findById(Long id);
    List<AssistedPerson> findAllByRelativeId(Long relativeId);
    List<AssistedPerson> findAll();

    /** The assisted persons who live in the household. */
    List<AssistedPerson> findAllByHouseholdId(Long householdId);

    /** The assisted persons whose person profile is the given one (normally one). */
    List<AssistedPerson> findAllByPersonProfileId(Long personProfileId);

}
