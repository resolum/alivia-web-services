package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomeByIdQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomesQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ServicedHome;

import java.util.List;
import java.util.Optional;

public interface ServicedHomeQueryService {
    List<ServicedHome> handle(GetServicedHomesQuery query);

    Optional<ServicedHome> handle(GetServicedHomeByIdQuery query);
}
