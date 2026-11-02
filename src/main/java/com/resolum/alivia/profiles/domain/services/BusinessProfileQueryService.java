package com.resolum.alivia.profiles.domain.services;

import java.util.Optional;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsBusinessProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetBusinessProfileByIdQuery;

public interface BusinessProfileQueryService {
    Optional<BusinessProfile> handle(GetBusinessProfileByIdQuery query);

    boolean handle(ExistsBusinessProfileByEmailAddressQuery query);
}
