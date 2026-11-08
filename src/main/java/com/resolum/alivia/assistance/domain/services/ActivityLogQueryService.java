package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByCaregiverIdQuery;

import java.util.List;

public interface ActivityLogQueryService {
    List<ActivityLog> handle(GetActivityLogsByAssistedPersonIdQuery query);
    List<ActivityLog> handle(GetActivityLogsByCaregiverIdQuery query);
}
