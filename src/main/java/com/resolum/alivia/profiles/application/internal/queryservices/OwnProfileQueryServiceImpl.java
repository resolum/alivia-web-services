package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.aggregates.Profile;
import com.resolum.alivia.profiles.domain.model.queries.GetOwnProfileQuery;
import com.resolum.alivia.profiles.domain.services.OwnProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;

/**
 * Implementation of {@link OwnProfileQueryService}.
 *
 * <p>A user has at most one profile: technicians own a staff profile and the other
 * profiled roles own a person profile. Business profiles are managed by the business
 * context and are not resolved here.</p>
 */
@Service
public class OwnProfileQueryServiceImpl implements OwnProfileQueryService {

    private final PersonProfileRepository personProfileRepository;
    private final StaffProfileRepository staffProfileRepository;

    public OwnProfileQueryServiceImpl(PersonProfileRepository personProfileRepository,
                                      StaffProfileRepository staffProfileRepository) {
        this.personProfileRepository = personProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public Optional<Profile> handle(GetOwnProfileQuery query) {
        return personProfileRepository.findByUserId(query.userId())
                .map(Profile.class::cast)
                .or(() -> staffProfileRepository.findByUserId(query.userId()).map(Profile.class::cast));
    }
}
