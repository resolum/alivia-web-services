package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ActivityLogQueryServiceImpl implements ActivityLogQueryService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogQueryServiceImpl(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    public List<ActivityLog> handle(GetActivityLogsByAssistedPersonIdQuery query) {
        return activityLogRepository.findAllByAssistedPersonIdAndDateBetween(
                query.assistedPersonId(), query.from(), query.to());
    }

    @Override
    public List<ActivityLog> handle(GetActivityLogsByCaregiverIdQuery query) {
        return activityLogRepository.findAllByAssignedCaregiverIdAndDateBetween(
                query.caregiverId(), query.from(), query.to());
    }
}
