package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.exceptions.CaregiverAlreadyRevokedException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationExpiredException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotPendingException;
import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistanceDomainTest {

    @Test
    @DisplayName("Relative and AssistedPerson can be created and managed relationship holds")
    void testRelativeAndAssistedPerson() {
        Relative relative = new Relative(new UserId(1L), new PersonProfileId(10L), new BusinessId(100L));
        AssistedPerson assistedPerson = new AssistedPerson(new PersonProfileId(20L), relative);

        assertThat(assistedPerson.isManagedBy(relative)).isTrue();
        assertThat(assistedPerson.getPersonProfileId().personProfileId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Caregiver lifecycle: active by default and can be revoked")
    void testCaregiverLifecycle() {
        Relative relative = new Relative(new UserId(1L), new PersonProfileId(10L), new BusinessId(100L));
        AssistedPerson assistedPerson = new AssistedPerson(new PersonProfileId(20L), relative);
        Caregiver caregiver = new Caregiver(new UserId(2L), assistedPerson);

        assertThat(caregiver.isActive()).isTrue();
        assertThat(caregiver.getStatus()).isEqualTo(CaregiverStatus.ACTIVE);
        assertThat(caregiver.isCaregiverOf(assistedPerson)).isTrue();

        caregiver.revoke();
        assertThat(caregiver.isActive()).isFalse();
        assertThat(caregiver.getStatus()).isEqualTo(CaregiverStatus.REVOKED);

        assertThatThrownBy(caregiver::revoke)
                .isInstanceOf(CaregiverAlreadyRevokedException.class);
    }

    @Test
    @DisplayName("Invitation lifecycle: pending, assign account, accept, renew, revoke and expiration check")
    void testInvitationLifecycle() {
        Relative relative = new Relative(new UserId(1L), new PersonProfileId(10L), new BusinessId(100L));
        AssistedPerson assistedPerson = new AssistedPerson(new PersonProfileId(20L), relative);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        EmailAddress email = new EmailAddress("cuidador@test.com");

        Invitation invitation = new Invitation(assistedPerson, relative, email, expiresAt);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.isExpired(LocalDateTime.now())).isFalse();

        invitation.assignCaregiverAccount(new UserId(3L), "https://setup.password");
        assertThat(invitation.getCaregiverUserId()).isEqualTo(new UserId(3L));

        invitation.accept(new UserId(3L), LocalDateTime.now());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);

        assertThatThrownBy(invitation::revoke)
                .isInstanceOf(InvitationNotPendingException.class);
    }

    @Test
    @DisplayName("Schedule calculation: occurrences, effective time range, closing date and overlaps")
    void testSchedule() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        TimeRange range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0));
        Schedule oneTime = Schedule.oneTime(date, range);

        assertThat(oneTime.occursOn(date)).isTrue();
        assertThat(oneTime.occursOn(date.plusDays(1))).isFalse();
        assertThat(oneTime.effectiveTimeRange()).isEqualTo(range);
        assertThat(oneTime.closingDate(date)).isEqualTo(date);

        WeeklyPeriod weeklyPeriod = new WeeklyPeriod(Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), range);
        Schedule weekly = Schedule.weekly(weeklyPeriod);
        // 2026-11-10 is Tuesday
        assertThat(weekly.occursOn(date)).isTrue();
        assertThat(oneTime.overlapsWith(weekly)).isTrue();
    }

    @Test
    @DisplayName("CareActivity assignment and occurrences")
    void testCareActivityAndAssignments() {
        Relative relative = new Relative(new UserId(1L), new PersonProfileId(10L), new BusinessId(100L));
        AssistedPerson assistedPerson = new AssistedPerson(new PersonProfileId(20L), relative);
        Caregiver caregiver = new Caregiver(new UserId(2L), assistedPerson);

        CareActivity activity = new CareActivity(assistedPerson, CareActivityType.MEDICATION, "Tomar medicina", "Cada 8 horas");
        assertThat(activity.isActive()).isTrue();

        LocalDate today = LocalDate.now();
        TimeRange range = new TimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0));
        Schedule schedule = Schedule.oneTime(today.plusDays(1), range);

        ActivityAssignment assignment = activity.assign(caregiver, schedule, today);
        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.isAssignedTo(caregiver)).isTrue();
        assertThat(activity.occurrencesOn(today.plusDays(1))).hasSize(1);
    }

    @Test
    @DisplayName("AgendaItem resolves status correctly")
    void testAgendaItemStatus() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 10, 10, 0);

        LocalDateTime before = LocalDateTime.of(2026, 11, 10, 7, 30);
        LocalDateTime during = LocalDateTime.of(2026, 11, 10, 9, 0);
        LocalDateTime after = LocalDateTime.of(2026, 11, 10, 11, 0);

        assertThat(AgendaItem.resolveStatus(start, end, before, null)).isEqualTo(OccurrenceStatus.SCHEDULED);
        assertThat(AgendaItem.resolveStatus(start, end, during, null)).isEqualTo(OccurrenceStatus.IN_PROGRESS);
        assertThat(AgendaItem.resolveStatus(start, end, after, null)).isEqualTo(OccurrenceStatus.MISSED);
        assertThat(AgendaItem.resolveStatus(start, end, before, ActivityLogStatus.COMPLETED)).isEqualTo(OccurrenceStatus.COMPLETED);
    }
}
