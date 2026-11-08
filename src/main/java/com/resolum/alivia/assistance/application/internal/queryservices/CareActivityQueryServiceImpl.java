package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivitiesByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivityByIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CareActivityQueryServiceImpl implements CareActivityQueryService {

    private final CareActivityRepository careActivityRepository;
    private final ActivityLogRepository activityLogRepository;

    public CareActivityQueryServiceImpl(CareActivityRepository careActivityRepository,
                                       ActivityLogRepository activityLogRepository) {
        this.careActivityRepository = careActivityRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    public Optional<CareActivity> handle(GetCareActivityByIdQuery query) {
        return careActivityRepository.findById(query.careActivityId());
    }

    @Override
    public List<CareActivity> handle(GetCareActivitiesByAssistedPersonIdQuery query) {
        return careActivityRepository.findAllByAssistedPersonId(query.assistedPersonId());
    }

    @Override
    public List<AgendaItem> handle(GetAgendaByAssistedPersonIdQuery query) {
        List<CareActivity> activities = careActivityRepository.findAllByAssistedPersonId(query.assistedPersonId());
        List<ActivityLog> logs = activityLogRepository.findAllByAssistedPersonIdAndDateBetween(
                query.assistedPersonId(), query.from(), query.to());

        LocalDateTime now = LocalDateTime.now();
        List<AgendaItem> items = new ArrayList<>();

        for (LocalDate date = query.from(); !date.isAfter(query.to()); date = date.plusDays(1)) {
            for (CareActivity activity : activities) {
                if (!activity.isActive()) {
                    continue;
                }
                for (ActivityAssignment assignment : activity.occurrencesOn(date)) {
                    final LocalDate currentDate = date;
                    ActivityLog log = logs.stream()
                            .filter(l -> Objects.equals(l.getAssignmentId(), assignment.getId()) && l.getDate().equals(currentDate))
                            .findFirst()
                            .orElse(null);

                    items.add(AgendaItem.of(activity, assignment, currentDate, log, now));
                }
            }
        }
        return items;
    }

    @Override
    public List<AgendaItem> handle(GetAgendaByCaregiverIdQuery query) {
        List<CareActivity> activities = careActivityRepository.findAllActive();
        List<ActivityLog> logs = activityLogRepository.findAllByAssignedCaregiverIdAndDateBetween(
                query.caregiverId(), query.from(), query.to());

        LocalDateTime now = LocalDateTime.now();
        List<AgendaItem> items = new ArrayList<>();

        for (LocalDate date = query.from(); !date.isAfter(query.to()); date = date.plusDays(1)) {
            for (CareActivity activity : activities) {
                for (ActivityAssignment assignment : activity.occurrencesOn(date)) {
                    if (assignment.isAssignedTo(null) || !Objects.equals(assignment.getCaregiverId(), query.caregiverId())) {
                        continue;
                    }
                    final LocalDate currentDate = date;
                    ActivityLog log = logs.stream()
                            .filter(l -> Objects.equals(l.getAssignmentId(), assignment.getId()) && l.getDate().equals(currentDate))
                            .findFirst()
                            .orElse(null);

                    items.add(AgendaItem.of(activity, assignment, currentDate, log, now));
                }
            }
        }
        return items;
    }
}
