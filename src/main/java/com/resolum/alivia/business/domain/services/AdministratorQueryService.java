package com.resolum.alivia.business.domain.services;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByUserIdQuery;

import java.util.Optional;

public interface AdministratorQueryService {
    Optional<Administrator> handle(GetAdministratorByIdQuery query);
    Optional<Administrator> handle(GetAdministratorByUserIdQuery query);
}
