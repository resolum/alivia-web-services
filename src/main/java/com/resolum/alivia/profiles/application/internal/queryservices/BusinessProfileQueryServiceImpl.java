package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsBusinessProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetBusinessProfileByIdQuery;
import com.resolum.alivia.profiles.domain.services.BusinessProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

@Service
public class BusinessProfileQueryServiceImpl implements BusinessProfileQueryService {

    private final BusinessProfileRepository businessProfileRepository;

    public BusinessProfileQueryServiceImpl(BusinessProfileRepository businessProfileRepository) {
        this.businessProfileRepository = businessProfileRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<BusinessProfile> handle(GetBusinessProfileByIdQuery query) {
        return businessProfileRepository.findById(query.profileId());
    }

    @Transactional(readOnly = true)
    @Override
    public boolean handle(ExistsBusinessProfileByEmailAddressQuery query) {
        return businessProfileRepository.existsByEmailAddress(new EmailAddress(query.emailAddress()));
    }
}
