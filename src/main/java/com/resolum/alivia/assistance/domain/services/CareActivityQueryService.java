package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivitiesByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivityByIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;

import java.util.List;
import java.util.Optional;

public interface CareActivityQueryService {
    Optional<CareActivity> handle(GetCareActivityByIdQuery query);
    List<CareActivity> handle(GetCareActivitiesByAssistedPersonIdQuery query);
    List<AgendaItem> handle(GetAgendaByAssistedPersonIdQuery query);
    List<AgendaItem> handle(GetAgendaByCaregiverIdQuery query);
}
