package com.resolum.alivia.nursing.domain.model.valueobjects;

import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One computed occurrence of a medication assignment, with its activity log if any.
 * Occurrences are not persisted; only the log is.
 *
 * @param id                      the administration identifier
 * @param careActivityId          the care activity identifier
 * @param medication              the medication, taken from the activity title
 * @param assistedPersonProfileId the assisted person profile identifier
 * @param assistedPersonName      the assisted person name, or {@code null} when it cannot be resolved
 * @param date                    the date the occurrence starts on
 * @param startTime               the scheduled start time
 * @param endTime                 the scheduled end time
 * @param startsAt                the actual start
 * @param endsAt                  the actual end, on the next day when the range crosses midnight
 * @param caregiverId             the assigned caregiver, or {@code null} when the relative does it
 * @param caregiverUserId         the user of the assigned caregiver, or {@code null} without an active membership
 * @param responsibleName         the responsible name, or {@code null} when it cannot be resolved
 * @param outcome                 the activity log, or {@code null} when nothing was recorded
 */
public record MedicationAdministration(
        MedicationAdministrationId id,
        Long careActivityId,
        String medication,
        PersonProfileId assistedPersonProfileId,
        String assistedPersonName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long caregiverId,
        UserId caregiverUserId,
        String responsibleName,
        ActivityLog outcome
) {

    /**
     * Returns the status derived from the activity log.
     *
     * @return the administration status
     */
    public MedicationAdministrationStatus status() {
        return statusOf(outcome == null ? null : outcome.getStatus());
    }

    /**
     * Resolves the status from the recorded log status.
     *
     * @param status the recorded status, or {@code null} when nothing was recorded
     * @return the administration status
     */
    public static MedicationAdministrationStatus statusOf(ActivityLogStatus status) {
        if (status == null) {
            return MedicationAdministrationStatus.PENDING;
        }
        return switch (status) {
            case COMPLETED -> MedicationAdministrationStatus.ADMINISTERED;
            case OMITTED -> MedicationAdministrationStatus.OMITTED;
            case MISSED -> MedicationAdministrationStatus.MISSED;
            case CANCELLED -> MedicationAdministrationStatus.CANCELLED;
        };
    }

    /**
     * Returns a copy with the given activity log.
     *
     * @param log the activity log
     * @return the updated administration
     */
    public MedicationAdministration withOutcome(ActivityLog log) {
        return new MedicationAdministration(id, careActivityId, medication, assistedPersonProfileId,
                assistedPersonName, date, startTime, endTime, startsAt, endsAt, caregiverId, caregiverUserId,
                responsibleName, log);
    }
}
