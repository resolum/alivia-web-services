package com.resolum.alivia.nursing.application.internal.queryservices;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.nursing.domain.model.queries.GetCareAgendaQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetPendingRemindersQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.CareActivityQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the care agenda and the pending reminders of the authenticated user.
 *
 * <p>Access is resolved first from the active memberships of the user: as relative, every
 * assignment of the assisted person is visible; as caregiver, only the assignments assigned to
 * that caregiver membership. Only active activities and assignments are listed. Items are then
 * computed from the schedules and their status from the activity logs and the current time in
 * {@code America/Lima}.</p>
 *
 * <p>Names are read from Profiles by person profile, for the assisted person and for the
 * assigned caregiver; the caregiver email is read from IAM by user. Both are batch reads.</p>
 */
@Slf4j
@Service
public class CareActivityQueryServiceImpl implements CareActivityQueryService {

    private static final ZoneId AGENDA_ZONE = ZoneId.of("America/Lima");
    private static final Comparator<AgendaItem> BY_START_THEN_PRIORITY =
            Comparator.comparing(AgendaItem::startsAt).thenComparing(AgendaItem::priority);

    private final CareActivityRepository careActivityRepository;
    private final CareMembershipRepository careMembershipRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ExternalProfilesService externalProfilesService;
    private final ExternalIamService externalIamService;
    private final Clock clock;
    private final int reminderHorizonDays;

    /**
     * Creates the service.
     *
     * @param careActivityRepository   the care activity repository
     * @param careMembershipRepository the membership repository
     * @param activityLogRepository    the activity log repository
     * @param externalProfilesService  the Profiles ACL used to resolve names
     * @param externalIamService       the IAM ACL used to resolve emails
     * @param clock                    the shared clock
     * @param reminderHorizonDays      how many days after today reminders cover
     */
    public CareActivityQueryServiceImpl(CareActivityRepository careActivityRepository,
                                        CareMembershipRepository careMembershipRepository,
                                        ActivityLogRepository activityLogRepository,
                                        ExternalProfilesService externalProfilesService,
                                        ExternalIamService externalIamService,
                                        Clock clock,
                                        @Value("${nursing.reminders.horizon-days:7}") int reminderHorizonDays) {
        this.careActivityRepository = careActivityRepository;
        this.careMembershipRepository = careMembershipRepository;
        this.activityLogRepository = activityLogRepository;
        this.externalProfilesService = externalProfilesService;
        this.externalIamService = externalIamService;
        this.clock = clock;
        this.reminderHorizonDays = reminderHorizonDays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AgendaItem> handle(GetCareAgendaQuery query) {
        log.debug("Handling care agenda query from {} to {}", query.from(), query.to());
        var scope = accessibleAssignments(query.userId(), query.assistedPersonProfileId());
        var items = agendaItems(scope, query.from(), query.to(), query.priority()).stream()
                .filter(item -> query.status() == null || item.status() == query.status())
                .sorted(BY_START_THEN_PRIORITY)
                .toList();
        log.info("Computed {} care agenda items for user id {}", items.size(), query.userId().userId());
        return items;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The window starts yesterday so overnight occurrences still in progress are included.</p>
     */
    @Override
    public List<AgendaItem> handle(GetPendingRemindersQuery query) {
        log.debug("Handling pending reminders query");
        var today = LocalDate.now(clock.withZone(AGENDA_ZONE));
        var scope = accessibleAssignments(query.userId(), query.assistedPersonProfileId());
        var items = agendaItems(scope, today.minusDays(1), today.plusDays(reminderHorizonDays), query.priority()).stream()
                .filter(AgendaItem::isPending)
                .sorted(BY_START_THEN_PRIORITY)
                .toList();
        log.info("Computed {} pending reminders for user id {}", items.size(), query.userId().userId());
        return items;
    }

    private List<ScopedAssignment> accessibleAssignments(UserId userId, PersonProfileId requestedPerson) {
        var memberships = careMembershipRepository.findActiveByUserId(userId).stream()
                .filter(membership -> requestedPerson == null
                        || membership.getAssistedPersonProfileId().equals(requestedPerson))
                .toList();
        if (requestedPerson != null && memberships.isEmpty()) {
            log.warn("Care agenda access denied: user id {} is not linked to assisted person {}",
                    userId.userId(), requestedPerson.personProfileId());
            throw new AssistedPersonAccessDeniedException(requestedPerson.personProfileId());
        }
        var relativeOf = memberships.stream().filter(membership -> membership.getRole() == CareRole.RELATIVE)
                .map(CareMembership::getAssistedPersonProfileId).collect(Collectors.toSet());
        var caregiverIds = memberships.stream().filter(membership -> membership.getRole() == CareRole.CAREGIVER)
                .map(CareMembership::getId).collect(Collectors.toSet());
        var persons = memberships.stream().map(CareMembership::getAssistedPersonProfileId).collect(Collectors.toSet());

        List<ScopedAssignment> scope = new ArrayList<>();
        for (CareActivity activity : careActivityRepository.findAllByAssistedPersonProfileIds(persons)) {
            if (!activity.isActive()) {
                continue;
            }
            boolean relative = relativeOf.contains(activity.getAssistedPersonProfileId());
            for (ActivityAssignment assignment : activity.getActivityAssignments().getActivityAssignments()) {
                // A single check per assignment, so a user with both roles never sees it twice.
                if (assignment.isActive() && (relative || caregiverIds.contains(assignment.getCaregiverId()))) {
                    scope.add(new ScopedAssignment(activity, assignment));
                }
            }
        }
        return scope;
    }

    private List<AgendaItem> agendaItems(List<ScopedAssignment> scope, LocalDate from, LocalDate to,
                                         ReminderPriority priority) {
        var selected = scope.stream()
                .filter(scoped -> priority == null || AgendaItem.priorityOf(scoped.activity().getType()) == priority)
                .toList();
        if (selected.isEmpty()) {
            return List.of();
        }
        var logs = logsByOccurrence(selected, from, to);
        var assistedPersons = selected.stream()
                .map(scoped -> scoped.activity().getAssistedPersonProfileId()).collect(Collectors.toSet());
        var caregivers = careMembershipRepository.findActiveByAssistedPersonProfileIds(assistedPersons).stream()
                .filter(membership -> membership.getRole() == CareRole.CAREGIVER)
                .collect(Collectors.toMap(CareMembership::getId, Function.identity()));
        var profilesToName = new HashSet<>(assistedPersons);
        caregivers.values().forEach(caregiver -> profilesToName.add(caregiver.getMemberPersonProfileId()));
        var personNames = externalProfilesService.fetchPersonNames(profilesToName);
        var caregiverUsers = caregivers.values().stream().map(CareMembership::getUserId).collect(Collectors.toSet());
        var emails = externalIamService.fetchEmails(caregiverUsers);
        var staffProfiles = externalIamService.fetchStaffProfileIds(caregiverUsers);
        var staffNames = externalProfilesService.fetchStaffNames(new HashSet<>(staffProfiles.values()));
        var now = LocalDateTime.now(clock.withZone(AGENDA_ZONE));

        List<AgendaItem> result = new ArrayList<>();
        for (LocalDate date : from.datesUntil(to.plusDays(1)).toList()) {
            for (ScopedAssignment scoped : selected) {
                var assignment = scoped.assignment();
                if (!assignment.occursOn(date)) {
                    continue;
                }
                var caregiver = caregivers.get(assignment.getCaregiverId());
                var caregiverProfile = caregiver == null ? null : caregiver.getMemberPersonProfileId();
                var contact = new AgendaItem.Contact(
                        personNames.get(scoped.activity().getAssistedPersonProfileId()),
                        caregiverProfile,
                        caregiver == null ? null : caregiverName(caregiver, personNames, staffProfiles, staffNames),
                        caregiver == null ? null : emails.get(caregiver.getUserId()));
                var activityLog = logs.get(new OccurrenceKey(assignment.getId(), date));
                result.add(AgendaItem.of(scoped.activity(), assignment, date, activityLog, now).withContact(contact));
            }
        }
        return result;
    }

    // Profile ids are per table, so a technician is named from its staff profile, never by person id.
    private static String caregiverName(CareMembership caregiver, Map<PersonProfileId, String> personNames,
                                        Map<UserId, Long> staffProfiles, Map<Long, String> staffNames) {
        var staffProfile = staffProfiles.get(caregiver.getUserId());
        return staffProfile != null ? staffNames.get(staffProfile) : personNames.get(caregiver.getMemberPersonProfileId());
    }

    private Map<OccurrenceKey, ActivityLog> logsByOccurrence(List<ScopedAssignment> scope, LocalDate from,
                                                             LocalDate to) {
        var assignmentIds = scope.stream().map(scoped -> scoped.assignment().getId()).toList();
        return activityLogRepository.findAllByAssignmentIdsBetween(assignmentIds, from, to).stream()
                .collect(Collectors.toMap(activityLog -> new OccurrenceKey(activityLog.getAssignmentId(), activityLog.getDate()),
                        Function.identity()));
    }

    private record ScopedAssignment(CareActivity activity, ActivityAssignment assignment) {
    }

    private record OccurrenceKey(Long assignmentId, LocalDate date) {
    }
}
