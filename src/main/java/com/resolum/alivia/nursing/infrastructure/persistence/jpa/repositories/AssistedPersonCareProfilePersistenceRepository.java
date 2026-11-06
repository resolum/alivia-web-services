package com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link AssistedPersonCareProfile}.
 */
public interface AssistedPersonCareProfilePersistenceRepository extends JpaRepository<AssistedPersonCareProfile, Long> {

    /**
     * Finds the profile registered by a user.
     *
     * @param registeredByUserId the user identifier
     * @return the profile, if any
     */
    Optional<AssistedPersonCareProfile> findByRegisteredByUserId(Long registeredByUserId);
}
