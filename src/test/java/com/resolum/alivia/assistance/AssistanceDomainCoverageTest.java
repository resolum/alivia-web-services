package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAccountNotAssignedException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.DayNotClosedYetException;
import com.resolum.alivia.assistance.domain.exceptions.InvalidScheduleException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationExpiredException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotForUserException;
import com.resolum.alivia.assistance.domain.exceptions.NotResponsibleForActivityException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceEndedException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceNotStartedException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceNotScheduledException;
import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.assistance.domain.model.valueobjects.EmergencyContact;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistanceDomainCoverageTest {

    private Relative relative;
    private AssistedPerson assistedPerson;
    private Caregiver caregiver;
    private CareActivity activity;
    private LocalDate occurrenceDate;
    private TimeRange daytime;

    @BeforeEach
    void setUp() {
        relative = new Relative(new UserId(1L), new PersonProfileId(10L), new BusinessId(100L));
        ReflectionTestUtils.setField(relative, "id", 1L);
        assistedPerson = new AssistedPerson(new PersonProfileId(20L), relative);
        ReflectionTestUtils.setField(assistedPerson, "id", 2L);
        caregiver = new Caregiver(new UserId(2L), assistedPerson);
        ReflectionTestUtils.setField(caregiver, "id", 3L);
        activity = new CareActivity(assistedPerson, CareActivityType.MEDICATION, " Medication ", " Daily ");
        ReflectionTestUtils.setField(activity, "id", 4L);
        occurrenceDate = LocalDate.now().plusDays(1);
        daytime = new TimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    @Test
    void assistedPersonUpdatesOnlyProvidedCareDetails() {
        EmergencyContact emergency = new EmergencyContact("Ana", "+51999999999");
        assistedPerson.updateCareDetails(50L, Kinship.PARENT, MobilityLevel.PARTIAL, emergency);
        assistedPerson.updateCareDetails(null, null, null, null);

        assertThat(assistedPerson.getHouseholdId()).isEqualTo(50L);
        assertThat(assistedPerson.getKinship()).isEqualTo(Kinship.PARENT);
        assertThat(assistedPerson.getEmergencyContact()).isEqualTo(emergency);
        assertThat(assistedPerson.isManagedBy(null)).isFalse();
    }

    @Test
    void schedulesCoverOneTimeWeeklyMidnightAndNonOverlappingCases() {
        Schedule first = Schedule.oneTime(occurrenceDate, daytime);
        Schedule disjoint = Schedule.oneTime(occurrenceDate, new TimeRange(LocalTime.of(11, 0), LocalTime.NOON));
        Schedule weekly = Schedule.weekly(new WeeklyPeriod(Set.of(occurrenceDate.getDayOfWeek()), daytime));
        Schedule otherWeekly = Schedule.weekly(new WeeklyPeriod(Set.of(occurrenceDate.getDayOfWeek()),
                new TimeRange(LocalTime.of(9, 30), LocalTime.of(11, 0))));
        Schedule overnight = Schedule.oneTime(occurrenceDate,
                new TimeRange(LocalTime.of(22, 0), LocalTime.of(6, 0)));

        assertThat(first.overlapsWith(disjoint)).isFalse();
        assertThat(weekly.overlapsWith(otherWeekly)).isTrue();
        assertThat(first.overlapsWith(weekly)).isTrue();
        assertThat(overnight.occurrenceEnd(occurrenceDate).toLocalDate()).isEqualTo(occurrenceDate.plusDays(1));
        assertThatThrownBy(() -> new Schedule(ScheduleType.ONE_TIME, null, daytime, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulesCoverEveryMixedAndBoundaryOverlapPath() {
        Schedule oneTime = Schedule.oneTime(occurrenceDate, daytime);
        Schedule overlapping = Schedule.oneTime(occurrenceDate,
                new TimeRange(LocalTime.of(9, 30), LocalTime.of(10, 30)));
        Schedule anotherDate = Schedule.oneTime(occurrenceDate.plusDays(2), daytime);
        Schedule weeklyAnotherDay = Schedule.weekly(new WeeklyPeriod(
                Set.of(occurrenceDate.plusDays(2).getDayOfWeek()), daytime));
        Schedule previousNight = Schedule.weekly(new WeeklyPeriod(
                Set.of(occurrenceDate.minusDays(1).getDayOfWeek()),
                new TimeRange(LocalTime.of(23, 0), LocalTime.of(9, 30))));
        Schedule overnight = Schedule.oneTime(occurrenceDate,
                new TimeRange(LocalTime.of(23, 0), LocalTime.of(2, 0)));
        Schedule nextDayWeekly = Schedule.weekly(new WeeklyPeriod(
                Set.of(occurrenceDate.plusDays(1).getDayOfWeek()),
                new TimeRange(LocalTime.of(1, 0), LocalTime.of(3, 0))));

        assertThat(oneTime.overlapsWith(overlapping)).isTrue();
        assertThat(oneTime.overlapsWith(anotherDate)).isFalse();
        assertThat(oneTime.overlapsWith(weeklyAnotherDay)).isFalse();
        assertThat(oneTime.overlapsWith(previousNight)).isTrue();
        assertThat(overnight.overlapsWith(nextDayWeekly)).isTrue();
        assertThat(oneTime.occursOn(occurrenceDate)).isTrue();
        assertThat(weeklyAnotherDay.occursOn(occurrenceDate)).isFalse();
        assertThatThrownBy(() -> oneTime.overlapsWith(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void agendaItemsResolveEveryStatusPriorityAndContactPath() {
        LocalDateTime startsAt = occurrenceDate.atTime(9, 0);
        LocalDateTime endsAt = occurrenceDate.atTime(10, 0);

        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, startsAt.minusMinutes(1), null))
                .isEqualTo(OccurrenceStatus.SCHEDULED);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, startsAt.plusMinutes(1), null))
                .isEqualTo(OccurrenceStatus.IN_PROGRESS);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, endsAt, null))
                .isEqualTo(OccurrenceStatus.MISSED);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, startsAt, ActivityLogStatus.COMPLETED))
                .isEqualTo(OccurrenceStatus.COMPLETED);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, startsAt, ActivityLogStatus.CANCELLED))
                .isEqualTo(OccurrenceStatus.CANCELLED);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, startsAt, ActivityLogStatus.MISSED))
                .isEqualTo(OccurrenceStatus.MISSED);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, startsAt, ActivityLogStatus.OMITTED))
                .isEqualTo(OccurrenceStatus.OMITTED);

        assertThat(AgendaItem.priorityOf(CareActivityType.MEDICATION)).isEqualTo(ReminderPriority.HIGH);
        assertThat(AgendaItem.priorityOf(CareActivityType.HYGIENE)).isEqualTo(ReminderPriority.MEDIUM);
        assertThat(AgendaItem.priorityOf(CareActivityType.APPOINTMENT)).isEqualTo(ReminderPriority.MEDIUM);
        assertThat(AgendaItem.priorityOf(CareActivityType.THERAPY)).isEqualTo(ReminderPriority.LOW);
        for (CareActivityType routine : Set.of(CareActivityType.MEAL, CareActivityType.BATH,
                CareActivityType.EXERCISE, CareActivityType.OTHER)) {
            assertThat(AgendaItem.priorityOf(routine)).isEqualTo(ReminderPriority.ROUTINE);
        }

        ActivityAssignment assignment = activity.assign(caregiver,
                Schedule.oneTime(occurrenceDate, daytime), LocalDate.now());
        ReflectionTestUtils.setField(assignment, "id", 5L);
        AgendaItem item = AgendaItem.of(activity, assignment, occurrenceDate, null, startsAt.minusMinutes(1));
        AgendaItem.Contact contact = new AgendaItem.Contact("Assisted", new PersonProfileId(30L),
                "Caregiver", "caregiver@alivia.test");
        AgendaItem contacted = item.withContact(contact);

        assertThat(item.isPending()).isTrue();
        assertThat(item.priority()).isEqualTo(ReminderPriority.HIGH);
        assertThat(contacted.contact()).isEqualTo(contact);
        assertThat(AgendaItem.resolveStatus(startsAt, endsAt, endsAt, ActivityLogStatus.COMPLETED))
                .isNotEqualTo(OccurrenceStatus.SCHEDULED);
        assertThatThrownBy(() -> AgendaItem.resolveStatus(null, endsAt, startsAt, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void careActivitySupportsUpdatesLifecycleAndAssignmentLifecycle() {
        Schedule schedule = Schedule.oneTime(occurrenceDate, daytime);
        ActivityAssignment assignment = activity.assign(caregiver, schedule, LocalDate.now());
        ReflectionTestUtils.setField(assignment, "id", 5L);

        activity.update(CareActivityType.THERAPY, " Therapy ", "  ");
        activity.deactivateAssignment(5L);
        assertThat(assignment.isActive()).isFalse();
        activity.activateAssignment(5L);
        activity.updateAssignment(5L, null, schedule, LocalDate.now());
        assertThat(assignment.isAssignedToRelative()).isTrue();
        activity.reassignToRelative(caregiver);
        activity.deactivate();
        assertThat(activity.isActive()).isFalse();
        activity.activate();

        assertThat(activity.getTitle()).isEqualTo("Therapy");
        assertThat(activity.getDescription()).isNull();
    }

    @Test
    void careActivityRejectsInactiveRevokedAndPastAssignments() {
        Schedule future = Schedule.oneTime(occurrenceDate, daytime);
        activity.deactivate();
        assertThatThrownBy(() -> activity.assign(caregiver, future, LocalDate.now()))
                .isInstanceOf(CareActivityNotActiveException.class);

        activity.activate();
        caregiver.revoke();
        assertThatThrownBy(() -> activity.assign(caregiver, future, LocalDate.now()))
                .isInstanceOf(CaregiverNotActiveException.class);

        Schedule past = Schedule.oneTime(LocalDate.now().minusDays(1), daytime);
        assertThatThrownBy(() -> activity.assign(null, past, LocalDate.now()))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void activityLogsCoverCompletedCancelledAndMissedOutcomes() {
        ActivityAssignment assignment = activity.assign(caregiver,
                Schedule.oneTime(occurrenceDate, daytime), LocalDate.now());
        ReflectionTestUtils.setField(assignment, "id", 5L);
        LocalDateTime during = occurrenceDate.atTime(9, 30);

        ActivityLog completed = ActivityLog.completed(activity, assignment, occurrenceDate, caregiver, during, " done ");
        ActivityLog cancelled = ActivityLog.cancelled(activity, assignment, occurrenceDate, during);
        ActivityAssignment oldAssignment = activity.assign(null,
                Schedule.oneTime(LocalDate.now().minusDays(2), daytime), LocalDate.now().minusDays(3));
        ReflectionTestUtils.setField(oldAssignment, "id", 6L);
        ActivityLog missed = ActivityLog.missed(activity, oldAssignment, LocalDate.now().minusDays(2), LocalDate.now());

        assertThat(completed.getStatus()).isEqualTo(ActivityLogStatus.COMPLETED);
        assertThat(completed.getNotes()).isEqualTo("done");
        assertThat(cancelled.getStatus()).isEqualTo(ActivityLogStatus.CANCELLED);
        assertThat(missed.getStatus()).isEqualTo(ActivityLogStatus.MISSED);
    }

    @Test
    void activityLogRejectsInvalidTimesResponsibilityAndInactiveAssignment() {
        ActivityAssignment assignment = activity.assign(caregiver,
                Schedule.oneTime(occurrenceDate, daytime), LocalDate.now());
        ReflectionTestUtils.setField(assignment, "id", 5L);

        assertThatThrownBy(() -> ActivityLog.completed(activity, assignment, occurrenceDate, caregiver,
                occurrenceDate.atTime(8, 59), null)).isInstanceOf(OccurrenceNotStartedException.class);
        assertThatThrownBy(() -> ActivityLog.completed(activity, assignment, occurrenceDate, caregiver,
                occurrenceDate.atTime(10, 1), null)).isInstanceOf(OccurrenceEndedException.class);

        Caregiver other = new Caregiver(new UserId(9L), assistedPerson);
        ReflectionTestUtils.setField(other, "id", 9L);
        assertThatThrownBy(() -> ActivityLog.completed(activity, assignment, occurrenceDate, other,
                occurrenceDate.atTime(9, 30), null)).isInstanceOf(NotResponsibleForActivityException.class);

        assignment.deactivate();
        assertThatThrownBy(() -> ActivityLog.cancelled(activity, assignment, occurrenceDate,
                occurrenceDate.atTime(9, 30))).isInstanceOf(OccurrenceNotScheduledException.class);
    }

    @Test
    void missedOutcomeRequiresTheOccurrenceDayToBeClosed() {
        ActivityAssignment assignment = activity.assign(null,
                Schedule.oneTime(occurrenceDate, daytime), LocalDate.now());
        assertThatThrownBy(() -> ActivityLog.missed(activity, assignment, occurrenceDate, occurrenceDate))
                .isInstanceOf(DayNotClosedYetException.class);
    }

    @Test
    void invitationCoversRenewExistingAccountExpirationAndIdentityChecks() {
        Invitation invitation = new Invitation(assistedPerson, relative, new EmailAddress("caregiver@alivia.test"),
                LocalDateTime.now().plusDays(2));
        invitation.renew(LocalDateTime.now().plusDays(5));
        invitation.assignExistingCaregiverAccount(new UserId(8L));
        assertThat(invitation.isExpired(null)).isFalse();
        assertThatThrownBy(() -> invitation.assignExistingCaregiverAccount(new UserId(9L)))
                .isInstanceOf(InvitationNotForUserException.class);

        Invitation withoutAccount = new Invitation(assistedPerson, relative, new EmailAddress("new@alivia.test"),
                LocalDateTime.now().plusDays(1));
        assertThatThrownBy(() -> withoutAccount.accept(new UserId(8L), LocalDateTime.now()))
                .isInstanceOf(CaregiverAccountNotAssignedException.class);

        Invitation expired = new Invitation(assistedPerson, relative, new EmailAddress("old@alivia.test"),
                LocalDateTime.now().minusDays(1));
        expired.assignExistingCaregiverAccount(new UserId(8L));
        assertThatThrownBy(() -> expired.accept(new UserId(8L), LocalDateTime.now()))
                .isInstanceOf(InvitationExpiredException.class);
    }
}
