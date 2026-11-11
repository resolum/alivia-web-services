package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativesByBusinessIdQuery;

import java.util.List;
import java.util.Optional;

public interface RelativeQueryService {
    Optional<Relative> handle(GetRelativeByIdQuery query);
    Optional<Relative> handle(GetRelativeByUserIdQuery query);
    List<Relative> handle(GetRelativesByBusinessIdQuery query);
}
