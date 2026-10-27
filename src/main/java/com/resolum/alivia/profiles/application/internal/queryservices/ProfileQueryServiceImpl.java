package com.resolum.alivia.profiles.application.internal.queryservices;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.queries.ExistsProfileByIdQuery;
import com.resolum.alivia.profiles.domain.services.ProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;

/**
 * Implementation of {@link ProfileQueryService}.
 *
 * <p>{@code Profile} is a mapped superclass with one table per profile kind, so the check
 * looks in the person, staff and business tables.</p>
 */
@Service
public class ProfileQueryServiceImpl implements ProfileQueryService {

    private final PersonProfileRepository personProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final BusinessProfileRepository businessProfileRepository;

    public ProfileQueryServiceImpl(PersonProfileRepository personProfileRepository,
                                   StaffProfileRepository staffProfileRepository,
                                   BusinessProfileRepository businessProfileRepository) {
        this.personProfileRepository = personProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.businessProfileRepository = businessProfileRepository;
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
        var id = query.profileId();
        return personProfileRepository.existsById(id)
                || staffProfileRepository.existsById(id)
                || businessProfileRepository.existsById(id);
    }
}
