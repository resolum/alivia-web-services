package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.ScheduleConflictException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.ActivateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.ActivateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.AssignCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.ReassignCaregiverActivitiesCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.CareActivityCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class CareActivityCommandServiceImpl implements CareActivityCommandService {

    private final CareActivityRepository careActivityRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public CareActivityCommandServiceImpl(CareActivityRepository careActivityRepository,
                                         AssistedPersonRepository assistedPersonRepository,
                                         RelativeRepository relativeRepository,
                                         CaregiverRepository caregiverRepository) {
        this.careActivityRepository = careActivityRepository;
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @Override
    public Long handle(CreateCareActivityCommand command) {
        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        AssistedPerson assistedPerson = assistedPersonRepository.findById(command.assistedPersonId())
                .orElseThrow(() -> new AssistedPersonNotFoundException(command.assistedPersonId()));

        if (!assistedPerson.isManagedBy(relative)) {
            throw new AssistedPersonNotManagedByRelativeException(assistedPerson.getId(), relative.getId());
        }

        CareActivity activity = new CareActivity(assistedPerson, command.type(), command.title(), command.description());
        activity = careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(UpdateCareActivityCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        activity.update(command.type(), command.title(), command.description());
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(ActivateCareActivityCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        activity.activate();
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(DeactivateCareActivityCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        activity.deactivate();
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(AssignCareActivityCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        Caregiver caregiver = null;
        if (command.caregiverId() != null) {
            caregiver = caregiverRepository.findById(command.caregiverId())
                    .orElseThrow(() -> new CaregiverNotFoundException(command.caregiverId()));
        }

        Schedule schedule = buildSchedule(command.scheduleType(), command.days(), command.date(),
                command.startTime(), command.endTime());

        if (caregiver != null) {
            validateScheduleConflict(caregiver, schedule, null);
        }

        activity.assign(caregiver, schedule, LocalDate.now());
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(UpdateActivityAssignmentCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        Caregiver caregiver = null;
        if (command.caregiverId() != null) {
            caregiver = caregiverRepository.findById(command.caregiverId())
                    .orElseThrow(() -> new CaregiverNotFoundException(command.caregiverId()));
        }

        Schedule schedule = buildSchedule(command.scheduleType(), command.days(), command.date(),
                command.startTime(), command.endTime());

        if (caregiver != null) {
            validateScheduleConflict(caregiver, schedule, command.assignmentId());
        }

        activity.updateAssignment(command.assignmentId(), caregiver, schedule, LocalDate.now());
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(ActivateActivityAssignmentCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        activity.activateAssignment(command.assignmentId());
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public Long handle(DeactivateActivityAssignmentCommand command) {
        CareActivity activity = careActivityRepository.findById(command.careActivityId())
                .orElseThrow(() -> new CareActivityNotFoundException(command.careActivityId()));

        validateOwnership(activity, command.relativeId());

        activity.deactivateAssignment(command.assignmentId());
        careActivityRepository.save(activity);
        return activity.getId();
    }

    @Override
    public void handle(ReassignCaregiverActivitiesCommand command) {
        Caregiver caregiver = caregiverRepository.findById(command.caregiverId())
                .orElseThrow(() -> new CaregiverNotFoundException(command.caregiverId()));

        List<CareActivity> activities = careActivityRepository.findAllByAssistedPersonId(command.assistedPersonId());
        for (CareActivity activity : activities) {
            activity.reassignToRelative(caregiver);
            careActivityRepository.save(activity);
        }
    }

    private void validateOwnership(CareActivity activity, Long relativeId) {
        Relative relative = relativeRepository.findById(relativeId)
                .orElseThrow(() -> new RelativeNotFoundException(relativeId));

        if (!activity.getAssistedPerson().isManagedBy(relative)) {
            throw new AssistedPersonNotManagedByRelativeException(activity.getAssistedPerson().getId(), relativeId);
        }
    }

    private Schedule buildSchedule(ScheduleType type, java.util.Set<java.time.DayOfWeek> days, LocalDate date,
                                  java.time.LocalTime start, java.time.LocalTime end) {
        TimeRange range = new TimeRange(start, end);
        if (type == ScheduleType.ONE_TIME) {
            return Schedule.oneTime(date, range);
        }
        return Schedule.weekly(new WeeklyPeriod(days, range));
    }

    private void validateScheduleConflict(Caregiver caregiver, Schedule newSchedule, Long excludeAssignmentId) {
        List<CareActivity> activities = careActivityRepository.findAllActive();
        for (CareActivity otherActivity : activities) {
            for (ActivityAssignment assignment : otherActivity.getActivityAssignments().getActivityAssignments()) {
                if (excludeAssignmentId != null && Objects.equals(assignment.getId(), excludeAssignmentId)) {
                    continue;
                }
                if (assignment.isActive() && assignment.isAssignedTo(caregiver)) {
                    if (assignment.getSchedule().overlapsWith(newSchedule)) {
                        throw new ScheduleConflictException("Caregiver already has an overlapping active assignment.");
                    }
                }
            }
        }
    }
}
