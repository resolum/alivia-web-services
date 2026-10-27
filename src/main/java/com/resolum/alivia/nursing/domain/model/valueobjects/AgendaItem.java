package com.resolum.alivia.nursing.domain.model.valueobjects;

import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One computed occurrence of an assignment in the care agenda. It is a read model and is not
 * persisted.
 *
 * @param careActivityId          the care activity identifier
 * @param assignmentId            the assignment (shift) identifier
 * @param title                   the activity title
 * @param type                    the activity type
 * @param assistedPersonProfileId the assisted person profile identifier
 * @param date                    the date the occurrence starts on
 * @param timeRange               the scheduled time range
 * @param startsAt                the actual start
 * @param endsAt                  the actual end, on the next day when the range crosses midnight
 * @param caregiverId             the assigned caregiver, or {@code null} when the relative does it
 * @param recurring               whether the assignment repeats weekly
 * @param status                  the computed status
 * @param contact                 names and email read from other contexts
 */
public record AgendaItem(
        Long careActivityId,
        Long assignmentId,
        String title,
        CareActivityType type,
        PersonProfileId assistedPersonProfileId,
        LocalDate date,
        TimeRange timeRange,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long caregiverId,
        boolean recurring,
        OccurrenceStatus status,
        Contact contact
) {

    private static final Contact NO_CONTACT = new Contact(null, null, null, null);

    /**
     * Computes the item of the occurrence that starts on the given date.
     *
     * <p>Without a log the status is scheduled before the start, in progress within the range
     * and missed after the end. With a log it is the logged result.</p>
     *
     * @param activity   the care activity
     * @param assignment the assignment
     * @param date       the date the occurrence starts on
     * @param log        the activity log of that occurrence, or {@code null}
     * @param now        the current time in America/Lima
     * @return the item, without contact data
     */
    public static AgendaItem of(CareActivity activity, ActivityAssignment assignment, LocalDate date,
                                ActivityLog log, LocalDateTime now) {
        var schedule = assignment.getSchedule();
        var startsAt = schedule.occurrenceStart(date);
        var endsAt = schedule.occurrenceEnd(date);
        return new AgendaItem(activity.getId(), assignment.getId(), activity.getTitle(), activity.getType(),
                activity.getAssistedPersonProfileId(), date, schedule.effectiveTimeRange(), startsAt, endsAt,
                assignment.getCaregiverId(), assignment.isRecurring(),
                resolveStatus(startsAt, endsAt, now, log == null ? null : log.getStatus()),
                NO_CONTACT);
    }

    /**
     * Resolves the status of an occurrence.
     *
     * <p>A recorded log always wins. Without one, the occurrence is scheduled before its
     * start, in progress until its end and missed afterwards.</p>
     *
     * @param startsAt  the actual start of the occurrence
     * @param endsAt    the actual end of the occurrence
     * @param now       the current local time
     * @param logStatus the recorded status, or {@code null} when nothing was recorded
     * @return the occurrence status
     */
    public static OccurrenceStatus resolveStatus(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now,
                                                 ActivityLogStatus logStatus) {
        Objects.requireNonNull(startsAt, "Start cannot be null.");
        Objects.requireNonNull(endsAt, "End cannot be null.");
        Objects.requireNonNull(now, "Current time cannot be null.");

        if (logStatus != null) {
            return switch (logStatus) {
                case COMPLETED -> OccurrenceStatus.COMPLETED;
                case CANCELLED -> OccurrenceStatus.CANCELLED;
                case MISSED -> OccurrenceStatus.MISSED;
                case OMITTED -> OccurrenceStatus.OMITTED;
            };
        }
        if (now.isBefore(startsAt)) {
            return OccurrenceStatus.SCHEDULED;
        }
        return now.isBefore(endsAt) ? OccurrenceStatus.IN_PROGRESS : OccurrenceStatus.MISSED;
    }

    /**
     * Derives the priority of an activity type.
     *
     * @param type the activity type
     * @return the priority for that type
     */
    public static ReminderPriority priorityOf(CareActivityType type) {
        return switch (type) {
            case MEDICATION -> ReminderPriority.HIGH;
            case APPOINTMENT -> ReminderPriority.MEDIUM;
            case THERAPY -> ReminderPriority.LOW;
            case MEAL, BATH, EXERCISE, OTHER -> ReminderPriority.ROUTINE;
        };
    }

    /**
     * Returns the priority derived from the activity type.
     *
     * @return the reminder priority
     */
    public ReminderPriority priority() {
        return priorityOf(type);
    }

    /**
     * Indicates whether the occurrence still needs attention, which makes it a pending reminder.
     *
     * @return {@code true} for scheduled and in progress occurrences
     */
    public boolean isPending() {
        return status == OccurrenceStatus.SCHEDULED || status == OccurrenceStatus.IN_PROGRESS;
    }

    /**
     * Returns a copy with the given contact data.
     *
     * @param contact the contact data
     * @return the updated item
     */
    public AgendaItem withContact(Contact contact) {
        return new AgendaItem(careActivityId, assignmentId, title, type, assistedPersonProfileId, date, timeRange,
                startsAt, endsAt, caregiverId, recurring, status, contact);
    }

    /**
     * Names and email read from Profiles and IAM. Any field is {@code null} when it cannot be resolved.
     *
     * @param assistedPersonName the assisted person name
     * @param caregiverProfileId the caregiver person profile
     * @param caregiverName      the caregiver name
     * @param caregiverEmail     the caregiver email
     */
    public record Contact(String assistedPersonName, PersonProfileId caregiverProfileId, String caregiverName,
                          String caregiverEmail) {
    }
}
