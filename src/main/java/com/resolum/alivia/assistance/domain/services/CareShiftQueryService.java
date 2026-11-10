package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftsQuery;

import java.util.List;
import java.util.Optional;

public interface CareShiftQueryService {
    List<CareShift> handle(GetShiftsQuery query);
    Optional<CareShift> handle(GetShiftByIdQuery query);
}
