package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.application.internal.queryservices.CareActivityQueryServiceImpl;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.InvalidAgendaRangeException;
import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.nursing.domain.model.queries.GetCareAgendaQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetPendingRemindersQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareActivityQueryServiceImpl Tests")
class CareActivityQueryServiceImplTest {

    // Monday 2026-10-26 10:00 in America/Lima (UTC-5).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate LONG_AGO = MONDAY.minusYears(1);
    private static final UserId CAREGIVER = new UserId(7L);
    private static final UserId OTHER_CAREGIVER = new UserId(9L);
    private static final UserId RELATIVE = new UserId(5L);
    private static final PersonProfileId PERSON = new PersonProfileId(8L);
    private static final PersonProfileId UNLINKED_PERSON = new PersonProfileId(20L);
    // Caregiver membership identifiers, used as caregiverId in the assignments.
    private static final Long CAREGIVER_ID = 1L;
    private static final Long OTHER_CAREGIVER_ID = 2L;

    @Mock
    private CareActivityRepository careActivityRepository;
    @Mock
    private CareMembershipRepository careMembershipRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private ExternalProfilesService externalProfilesService;
    @Mock
    private ExternalIamService externalIamService;

    private CareActivityQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CareActivityQueryServiceImpl(careActivityRepository, careMembershipRepository,
                activityLogRepository, externalProfilesService, externalIamService, CLOCK, 7);
    }

    @Test
    @DisplayName("A caregiver only sees the assignments assigned to them, with contact data")
    void caregiverSeesOwnAssignments() {
        givenMemberships(CAREGIVER, caregiverMembership());
        givenAgendaActivities();
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(Set.of(PERSON)))
                .thenReturn(List.of(caregiverMembership()));
        when(externalProfilesService.fetchPersonNames(Set.of(PERSON, profileOf(CAREGIVER))))
                .thenReturn(Map.of(PERSON, "Maria Lopez", profileOf(CAREGIVER), "Ana Torres"));
        when(externalIamService.fetchEmails(Set.of(CAREGIVER))).thenReturn(Map.of(CAREGIVER, "ana.torres@example.com"));

        var agenda = service.handle(agendaQuery(CAREGIVER, null, null, null));

        assertThat(agenda).extracting(AgendaItem::assignmentId).containsExactly(101L);
        var item = agenda.getFirst();
        assertThat(item.status()).isEqualTo(OccurrenceStatus.MISSED);
        assertThat(item.caregiverId()).isEqualTo(CAREGIVER_ID);
        assertThat(item.contact()).isEqualTo(new AgendaItem.Contact("Maria Lopez", profileOf(CAREGIVER),
                "Ana Torres", "ana.torres@example.com"));
    }

    @Test
    @DisplayName("A relative sees every assignment of the assisted person, sorted by start")
    void relativeSeesAllAssignments() {
        givenMemberships(RELATIVE, membership(RELATIVE, CareRole.RELATIVE, 3L));
        givenAgendaActivities();

        var agenda = service.handle(agendaQuery(RELATIVE, null, null, null));

        assertThat(agenda).extracting(AgendaItem::assignmentId).containsExactly(101L, 102L, 103L);
        assertThat(agenda.getLast().status()).isEqualTo(OccurrenceStatus.SCHEDULED);
        assertThat(agenda.getLast().recurring()).isTrue();
        assertThat(agenda.getLast().caregiverId()).isNull();
        // No membership, name or email is resolvable here: the fields stay null instead of failing.
        assertThat(agenda).allSatisfy(item ->
                assertThat(item.contact()).isEqualTo(new AgendaItem.Contact(null, null, null, null)));
    }

    @Test
    @DisplayName("A user with both roles gets each assignment and date once, with names and emails in one batch")
    void bothRolesAreMergedWithoutDuplicates() {
        givenMemberships(CAREGIVER, caregiverMembership(), membership(CAREGIVER, CareRole.RELATIVE, 4L));
        givenAgendaActivities();
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(Set.of(PERSON)))
                .thenReturn(List.of(caregiverMembership(), membership(OTHER_CAREGIVER, CareRole.CAREGIVER, OTHER_CAREGIVER_ID)));

        var agenda = service.handle(agendaQuery(CAREGIVER, null, null, null));

        assertThat(agenda).extracting(AgendaItem::assignmentId).containsExactly(101L, 102L, 103L);
        verify(externalProfilesService, times(1)).fetchPersonNames(any());
        verify(careMembershipRepository, times(1)).findActiveByAssistedPersonProfileIds(Set.of(PERSON));
        verify(externalIamService, times(1)).fetchEmails(Set.of(CAREGIVER, OTHER_CAREGIVER));
    }

    @Test
    @DisplayName("Requesting an assisted person the user is not linked to is denied before reading the agenda")
    void unlinkedPersonIsDenied() {
        givenMemberships(CAREGIVER, caregiverMembership());

        assertThatThrownBy(() -> service.handle(agendaQuery(CAREGIVER, UNLINKED_PERSON, null, null)))
                .isInstanceOf(AssistedPersonAccessDeniedException.class);
        verify(careActivityRepository, never()).findAllByAssistedPersonProfileIds(any());
    }

    @Test
    @DisplayName("Filters by assisted person, status and priority")
    void filters() {
        givenMemberships(RELATIVE, membership(RELATIVE, CareRole.RELATIVE, 3L));
        givenAgendaActivities();

        assertThat(service.handle(agendaQuery(RELATIVE, PERSON, OccurrenceStatus.SCHEDULED, null)))
                .extracting(AgendaItem::assignmentId).containsExactly(103L);
        assertThat(service.handle(agendaQuery(RELATIVE, PERSON, null, ReminderPriority.HIGH)))
                .extracting(AgendaItem::assignmentId).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("An activity log sets the occurrence status")
    void loggedOccurrence() {
        givenMemberships(CAREGIVER, caregiverMembership());
        givenAgendaActivities();
        when(activityLogRepository.findAllByAssignmentIdsBetween(anyCollection(), eq(MONDAY), eq(MONDAY)))
                .thenReturn(List.of(log(101L, MONDAY, ActivityLogStatus.COMPLETED)));

        var agenda = service.handle(agendaQuery(CAREGIVER, null, null, null));

        assertThat(agenda.getFirst().status()).isEqualTo(OccurrenceStatus.COMPLETED);
    }

    @Test
    @DisplayName("Inactive activities and inactive assignments are not listed")
    void inactiveAreSkipped() {
        givenMemberships(RELATIVE, membership(RELATIVE, CareRole.RELATIVE, 3L));
        var inactiveActivity = activity(1L, CareActivityType.MEAL);
        oneOff(inactiveActivity, 201L, MONDAY, 14, 15);
        ReflectionTestUtils.setField(inactiveActivity, "status", ActivityStatus.INACTIVE);
        var active = activity(2L, CareActivityType.MEAL);
        var inactiveAssignment = oneOff(active, 202L, MONDAY, 14, 15);
        ReflectionTestUtils.setField(inactiveAssignment, "status", ActivityStatus.INACTIVE);
        oneOff(active, 203L, MONDAY, 14, 15);
        when(careActivityRepository.findAllByAssistedPersonProfileIds(Set.of(PERSON)))
                .thenReturn(List.of(inactiveActivity, active));

        assertThat(service.handle(agendaQuery(RELATIVE, null, null, null)))
                .extracting(AgendaItem::assignmentId).containsExactly(203L);
    }

    @Test
    @DisplayName("Reminders cover yesterday to today plus the horizon, keep pending ones and sort by start then priority")
    void pendingReminders() {
        givenMemberships(RELATIVE, membership(RELATIVE, CareRole.RELATIVE, 3L));
        var overnight = activity(1L, CareActivityType.OTHER);
        oneOff(overnight, 201L, MONDAY.minusDays(1), 22, 11);
        var missedToday = activity(2L, CareActivityType.MEDICATION);
        oneOff(missedToday, 202L, MONDAY, 8, 9);
        var therapy = activity(3L, CareActivityType.THERAPY);
        oneOff(therapy, 203L, MONDAY, 14, 15);
        var medication = activity(4L, CareActivityType.MEDICATION);
        oneOff(medication, 204L, MONDAY, 14, 15);
        var cancelled = activity(5L, CareActivityType.APPOINTMENT);
        oneOff(cancelled, 205L, MONDAY.plusDays(1), 10, 11);
        var lastDay = activity(6L, CareActivityType.APPOINTMENT);
        oneOff(lastDay, 206L, MONDAY.plusDays(7), 10, 11);
        var beyondHorizon = activity(7L, CareActivityType.APPOINTMENT);
        oneOff(beyondHorizon, 207L, MONDAY.plusDays(8), 10, 11);
        when(careActivityRepository.findAllByAssistedPersonProfileIds(Set.of(PERSON)))
                .thenReturn(List.of(overnight, missedToday, therapy, medication, cancelled, lastDay, beyondHorizon));
        when(activityLogRepository.findAllByAssignmentIdsBetween(anyCollection(), eq(MONDAY.minusDays(1)),
                eq(MONDAY.plusDays(7))))
                .thenReturn(List.of(log(205L, MONDAY.plusDays(1), ActivityLogStatus.CANCELLED)));

        var reminders = service.handle(new GetPendingRemindersQuery(RELATIVE, null, null));

        assertThat(reminders).extracting(AgendaItem::assignmentId).containsExactly(201L, 204L, 203L, 206L);
        assertThat(reminders.getFirst().status()).isEqualTo(OccurrenceStatus.IN_PROGRESS);
        assertThat(reminders).extracting(AgendaItem::priority).containsExactly(
                ReminderPriority.ROUTINE, ReminderPriority.HIGH, ReminderPriority.LOW, ReminderPriority.MEDIUM);
    }

    @Test
    @DisplayName("Reminders apply the same access rules as the agenda")
    void remindersDenyUnlinkedPerson() {
        givenMemberships(CAREGIVER, caregiverMembership());

        assertThatThrownBy(() -> service.handle(new GetPendingRemindersQuery(CAREGIVER, UNLINKED_PERSON, null)))
                .isInstanceOf(AssistedPersonAccessDeniedException.class);
    }

    @Test
    @DisplayName("An invalid date range is rejected before the service runs")
    void invalidDateRange() {
        assertThatThrownBy(() -> service.handle(new GetCareAgendaQuery(CAREGIVER, MONDAY, MONDAY.minusDays(1),
                null, null, null)))
                .isInstanceOf(InvalidAgendaRangeException.class);
        verify(careMembershipRepository, never()).findActiveByUserId(any());
    }

    @Test
    @DisplayName("A user without memberships gets an empty agenda")
    void noMemberships() {
        givenMemberships(CAREGIVER);
        when(careActivityRepository.findAllByAssistedPersonProfileIds(Set.of())).thenReturn(List.of());

        assertThat(service.handle(agendaQuery(CAREGIVER, null, null, null))).isEmpty();
    }

    private void givenMemberships(UserId userId, CareMembership... memberships) {
        when(careMembershipRepository.findActiveByUserId(userId)).thenReturn(List.of(memberships));
    }

    // Assignment 101: caregiver 1, 08:00-09:00 (MISSED at 10:00). 102: caregiver 2, same slot.
    // 103: the relative, weekly on Monday and Wednesday 14:00-15:00 (SCHEDULED).
    private void givenAgendaActivities() {
        var medication = activity(1L, CareActivityType.MEDICATION);
        withId(medication.assign(CAREGIVER_ID, Schedule.oneTime(MONDAY, range(8, 9)), LONG_AGO), 101L);
        withId(medication.assign(OTHER_CAREGIVER_ID, Schedule.oneTime(MONDAY, range(8, 9)), LONG_AGO), 102L);
        var therapy = activity(2L, CareActivityType.THERAPY);
        withId(therapy.assign(null, Schedule.weekly(
                new WeeklyPeriod(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), range(14, 15))), LONG_AGO), 103L);
        when(careActivityRepository.findAllByAssistedPersonProfileIds(Set.of(PERSON)))
                .thenReturn(List.of(medication, therapy));
    }

    private static GetCareAgendaQuery agendaQuery(UserId userId, PersonProfileId person, OccurrenceStatus status,
                                                  ReminderPriority priority) {
        return new GetCareAgendaQuery(userId, MONDAY, MONDAY, person, status, priority);
    }

    private static CareMembership caregiverMembership() {
        return membership(CAREGIVER, CareRole.CAREGIVER, CAREGIVER_ID);
    }

    private static CareMembership membership(UserId userId, CareRole role, Long id) {
        var membership = new CareMembership(userId, profileOf(userId), PERSON, role);
        ReflectionTestUtils.setField(membership, "id", id);
        return membership;
    }

    private static PersonProfileId profileOf(UserId userId) {
        return new PersonProfileId(userId.userId() * 10);
    }

    private static CareActivity activity(Long id, CareActivityType type) {
        var activity = new CareActivity(PERSON, type, type.name() + " activity", null);
        ReflectionTestUtils.setField(activity, "id", id);
        return activity;
    }

    private static ActivityAssignment oneOff(
            CareActivity activity, Long id, LocalDate date, int startHour, int endHour) {
        return withId(activity.assign(null, Schedule.oneTime(date, range(startHour, endHour)), LONG_AGO), id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static ActivityLog log(Long assignmentId, LocalDate date, ActivityLogStatus status) {
        var log = BeanUtils.instantiateClass(ActivityLog.class);
        ReflectionTestUtils.setField(log, "assignmentId", assignmentId);
        ReflectionTestUtils.setField(log, "date", date);
        ReflectionTestUtils.setField(log, "status", status);
        return log;
    }

    private static TimeRange range(int startHour, int endHour) {
        return new TimeRange(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }
}
