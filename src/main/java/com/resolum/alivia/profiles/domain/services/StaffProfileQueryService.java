package com.resolum.alivia.profiles.domain.services;

import java.util.Optional;

import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetStaffProfileByIdQuery;

public interface StaffProfileQueryService {
    Optional<StaffProfile> handle(GetStaffProfileByIdQuery query);

    boolean handle(ExistsStaffProfileByEmailAddressQuery query);

    boolean handle(ExistsStaffProfileByDniQuery query);
}
