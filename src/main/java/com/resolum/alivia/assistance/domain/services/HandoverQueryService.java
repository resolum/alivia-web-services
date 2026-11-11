package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoverByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoversQuery;

import java.util.List;
import java.util.Optional;

public interface HandoverQueryService {
    List<Handover> handle(GetHandoversQuery query);
    Optional<Handover> handle(GetHandoverByIdQuery query);
}
