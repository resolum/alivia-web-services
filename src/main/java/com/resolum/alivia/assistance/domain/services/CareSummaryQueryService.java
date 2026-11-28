package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.queries.GetCareNotesQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareSummaryQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary;

import java.util.List;

public interface CareSummaryQueryService {
    List<CareNote> handle(GetCareNotesQuery query);

    CareSummary handle(GetCareSummaryQuery query);
}
