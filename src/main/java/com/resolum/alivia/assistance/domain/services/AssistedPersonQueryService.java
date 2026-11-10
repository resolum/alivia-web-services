package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByUserIdQuery;

import java.util.List;
import java.util.Optional;

public interface AssistedPersonQueryService {
    List<AssistedPerson> handle(GetAssistedPersonsByRelativeIdQuery query);
    Optional<AssistedPerson> handle(GetAssistedPersonByIdQuery query);
    List<AssistedPerson> handle(GetAssistedPersonsByUserIdQuery query);
    List<AssistedPerson> handle(GetAllAssistedPersonsQuery query);
}
