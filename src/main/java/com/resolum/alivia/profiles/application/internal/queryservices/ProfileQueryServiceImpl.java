package com.resolum.alivia.profiles.application.internal.queryservices;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.queries.ExistsProfileByIdQuery;
import com.resolum.alivia.profiles.domain.services.ProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.ProfilePersistenceRepository;

/**
 * Implementation of {@link ProfileQueryService}.
 */
@Service
public class ProfileQueryServiceImpl implements ProfileQueryService {

    private final ProfilePersistenceRepository profilePersistenceRepository;

    public ProfileQueryServiceImpl(ProfilePersistenceRepository profilePersistenceRepository) {
        this.profilePersistenceRepository = profilePersistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public boolean handle(ExistsProfileByIdQuery query) {
        if (query == null || query.profileId() == null || query.profileId() <= 0) {
            return false;
        }
        return profilePersistenceRepository.existsById(query.profileId());
    }
}
