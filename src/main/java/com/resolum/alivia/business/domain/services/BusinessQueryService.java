package com.resolum.alivia.business.domain.services;

import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByAdministratorIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;

import java.util.Optional;

public interface BusinessQueryService {
    Optional<Business> handle(GetBusinessByAdministratorIdQuery query);
    Optional<Business> handle(GetBusinessByIdQuery query);
}
