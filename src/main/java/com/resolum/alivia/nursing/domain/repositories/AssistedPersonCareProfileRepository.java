package com.resolum.alivia.nursing.domain.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Optional;

/**
 * Repository of assisted person care profiles.
 */
public interface AssistedPersonCareProfileRepository {

    /**
     * Saves a care profile.
     *
     * @param profile the profile
     * @return the saved profile
     */
    AssistedPersonCareProfile save(AssistedPersonCareProfile profile);

    /**
     * Finds the profile registered by a responsible client.
     *
     * @param registeredBy the responsible client
     * @return the profile, if any
     */
    Optional<AssistedPersonCareProfile> findByRegisteredBy(UserId registeredBy);
}
