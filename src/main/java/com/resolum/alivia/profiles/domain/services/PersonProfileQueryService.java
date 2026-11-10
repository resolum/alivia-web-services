package com.resolum.alivia.profiles.domain.services;

import java.util.Optional;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByUserIdQuery;

public interface PersonProfileQueryService {
    Optional<PersonProfile> handle(GetPersonProfileByIdQuery query);

    default Optional<PersonProfile> handle(GetPersonProfileByUserIdQuery query) {
        return Optional.empty();
    }

    boolean handle(ExistsPersonProfileByEmailAddressQuery query);

    boolean handle(ExistsPersonProfileByDniQuery query);
}
