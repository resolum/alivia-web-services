package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceClosedException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.MarkMissedActivitiesCommand;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ActivityLogCommandServiceImpl implements ActivityLogCommandService {

    private final ActivityLogRepository activityLogRepository;
    private final CareActivityRepository careActivityRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public ActivityLogCommandServiceImpl(ActivityLogRepository activityLogRepository,
                                        CareActivityRepository careActivityRepository,
                                        RelativeRepository relativeRepository,
                                        CaregiverRepository caregiverRepository) {
        this.activityLogRepository = activityLogRepository;
        this.careActivityRepository = careActivityRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @Override
    public Long handle(CompleteActivityCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        ActivityAssignment assignment = activity.getActivityAssignments().getAssignmentWithId(command.assignmentId());

        if (activityLogRepository.existsByAssignmentIdAndDate(command.assignmentId(), command.date())) {
            throw new OccurrenceClosedException(command.assignmentId(), command.date());
        }

        Caregiver completedBy = null;
        if (command.caregiverUserId() != null) {
            completedBy = caregiverRepository.findByAssistedPersonIdAndUserIdAndStatus(
                    activity.getAssistedPerson().getId(),
                    new UserId(command.caregiverUserId()),
                    CaregiverStatus.ACTIVE
            ).orElse(null);
        }

        ActivityLog log = ActivityLog.completed(activity, assignment, command.date(), completedBy, LocalDateTime.now(), command.notes());
        log = activityLogRepository.save(log);
        return log.getId();
    }

    @Override
    public Long handle(CancelActivityOccurrenceCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        if (!activity.getAssistedPerson().isManagedBy(relative)) {
            throw new AssistedPersonNotManagedByRelativeException(activity.getAssistedPerson().getId(), relative.getId());
        }

        ActivityAssignment assignment = activity.getActivityAssignments().getAssignmentWithId(command.assignmentId());

        if (activityLogRepository.existsByAssignmentIdAndDate(command.assignmentId(), command.date())) {
            throw new OccurrenceClosedException(command.assignmentId(), command.date());
        }

        ActivityLog log = ActivityLog.cancelled(activity, assignment, command.date(), LocalDateTime.now());
        log = activityLogRepository.save(log);
        return log.getId();
    }

    @Override
    public void handle(MarkMissedActivitiesCommand command) {
        LocalDate today = LocalDate.now();
        List<CareActivity> activities = careActivityRepository.findAllActive();

        for (CareActivity activity : activities) {
            for (ActivityAssignment assignment : activity.getActivityAssignments().getActivityAssignments()) {
                if (!assignment.isActive()) {
                    continue;
                }
                // Check occurrences that close on command.closingDate()
                LocalDate date = command.closingDate();
                if (assignment.occursOn(date)) {
                    if (!activityLogRepository.existsByAssignmentIdAndDate(assignment.getId(), date)) {
                        ActivityLog log = ActivityLog.missed(activity, assignment, date, today);
                        activityLogRepository.save(log);
                    }
                }
            }
        }
    }
}
