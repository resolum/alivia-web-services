package com.resolum.alivia.profiles.domain.services;

import java.util.Optional;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;

public interface PersonProfileQueryService {
    Optional<PersonProfile> handle(GetPersonProfileByIdQuery query);

    boolean handle(ExistsPersonProfileByEmailAddressQuery query);

    boolean handle(ExistsPersonProfileByDniQuery query);
}
