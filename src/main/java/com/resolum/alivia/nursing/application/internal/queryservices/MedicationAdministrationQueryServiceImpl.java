package com.resolum.alivia.nursing.application.internal.queryservices;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationAdministrationQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Computes a medication administration from its assignment and the activity log.
 *
 * <p>Access follows the agenda: an active relative of the assisted person sees every
 * administration; an active caregiver only those of the assignments assigned to them. Inactive
 * activities and assignments are not found.</p>
 */
@Slf4j
@Service
public class MedicationAdministrationQueryServiceImpl implements MedicationAdministrationQueryService {

    private final CareActivityRepository careActivityRepository;
    private final CareMembershipRepository careMembershipRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ExternalProfilesService externalProfilesService;
    private final ExternalIamService externalIamService;

    /**
     * Creates the service.
     *
     * @param careActivityRepository   the care activity repository
     * @param careMembershipRepository the membership repository
     * @param activityLogRepository    the activity log repository
     * @param externalProfilesService  the Profiles ACL used to resolve names
     * @param externalIamService       the IAM ACL used to find the staff profile of technicians
     */
    public MedicationAdministrationQueryServiceImpl(CareActivityRepository careActivityRepository,
                                                    CareMembershipRepository careMembershipRepository,
                                                    ActivityLogRepository activityLogRepository,
                                                    ExternalProfilesService externalProfilesService,
                                                    ExternalIamService externalIamService) {
        this.careActivityRepository = careActivityRepository;
        this.careMembershipRepository = careMembershipRepository;
        this.activityLogRepository = activityLogRepository;
        this.externalProfilesService = externalProfilesService;
        this.externalIamService = externalIamService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MedicationAdministration handle(GetMedicationAdministrationQuery query) {
        var id = query.administrationId();
        log.debug("Handling get medication administration query for {}", id);
        var activity = careActivityRepository.findByAssignmentId(id.assignmentId())
                .filter(found -> found.getType() == CareActivityType.MEDICATION && found.isActive())
                .orElseThrow(() -> notFound(id));
        var assignment = activity.occurrencesOn(id.date()).stream()
                .filter(candidate -> candidate.getId().equals(id.assignmentId()))
                .findFirst()
                .orElseThrow(() -> notFound(id));
        var person = activity.getAssistedPersonProfileId();

        boolean allowed = careMembershipRepository.findActiveByUserId(query.userId()).stream()
                .filter(membership -> membership.getAssistedPersonProfileId().equals(person))
                .anyMatch(membership -> membership.getRole() == CareRole.RELATIVE
                        || assignment.isAssignedTo(membership.getId()));
        if (!allowed) {
            log.warn("Medication administration {} access denied for user id {}", id, query.userId().userId());
            throw new MedicationAdministrationAccessDeniedException(id);
        }

        var caregiver = assignment.isAssignedToRelative() ? null
                : careMembershipRepository.findActiveByAssistedPersonProfileIds(Set.of(person)).stream()
                .filter(membership -> assignment.isAssignedTo(membership.getId()))
                .findFirst()
                .orElse(null);
        var names = externalProfilesService.fetchPersonNames(
                caregiver == null ? Set.of(person) : Set.of(person, caregiver.getMemberPersonProfileId()));
        var caregiverStaffProfile = caregiver == null ? null
                : externalIamService.fetchStaffProfileIds(Set.of(caregiver.getUserId())).get(caregiver.getUserId());
        // Profile ids are per table, so a technician is named from its staff profile, never by person id.
        var caregiverName = caregiver == null ? null
                : caregiverStaffProfile != null
                ? externalProfilesService.fetchStaffNames(Set.of(caregiverStaffProfile)).get(caregiverStaffProfile)
                : names.get(caregiver.getMemberPersonProfileId());
        var activityLog = activityLogRepository.findByAssignmentIdAndDate(id.assignmentId(), id.date()).orElse(null);
        var schedule = assignment.getSchedule();

        return new MedicationAdministration(id, activity.getId(), activity.getTitle(), person, names.get(person),
                id.date(), schedule.effectiveTimeRange().start(), schedule.effectiveTimeRange().end(),
                schedule.occurrenceStart(id.date()), schedule.occurrenceEnd(id.date()),
                assignment.getCaregiverId(),
                caregiver == null ? null : caregiver.getUserId(),
                caregiverName,
                activityLog);
    }

    private static MedicationAdministrationNotFoundException notFound(MedicationAdministrationId id) {
        log.warn("Medication administration {} not found", id);
        return new MedicationAdministrationNotFoundException(id);
    }
}
