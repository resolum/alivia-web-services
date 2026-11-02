package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;

import java.util.List;
import java.util.Optional;

public interface IncidentQueryService {
    List<Incident> handle(GetIncidentsQuery query);

    Optional<Incident> handle(GetIncidentByIdQuery query);
}
