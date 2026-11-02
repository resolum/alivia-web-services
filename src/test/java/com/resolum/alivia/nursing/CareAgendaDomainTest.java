package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.domain.exceptions.ActivityAssignmentNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.InvalidAgendaRangeException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.queries.GetCareAgendaQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Care agenda domain Tests")
class CareAgendaDomainTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final UserId CAREGIVER = new UserId(7L);
    private static final TimeRange NIGHT = new TimeRange(LocalTime.of(22, 0), LocalTime.of(6, 0));
    private static final TimeRange AFTERNOON = new TimeRange(LocalTime.of(14, 0), LocalTime.of(15, 0));

    @ParameterizedTest
    @CsvSource({"MEDICATION,HIGH", "APPOINTMENT,MEDIUM", "THERAPY,LOW", "MEAL,ROUTINE", "BATH,ROUTINE",
            "EXERCISE,ROUTINE", "OTHER,ROUTINE"})
    @DisplayName("AgendaItem.priorityOf derives the priority from the activity type")
    void priorityFromType(CareActivityType type, ReminderPriority expected) {
        assertThat(AgendaItem.priorityOf(type)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ReminderPriority natural order goes from most to least important")
    void priorityOrder() {
        var sorted = Arrays.stream(ReminderPriority.values()).sorted().toList();
        assertThat(sorted).containsExactly(ReminderPriority.HIGH, ReminderPriority.MEDIUM,
                ReminderPriority.LOW, ReminderPriority.ROUTINE);
    }

    @ParameterizedTest
    @CsvSource({"07:59,SCHEDULED", "08:00,IN_PROGRESS", "08:59,IN_PROGRESS", "09:00,MISSED"})
    @DisplayName("AgendaItem.resolveStatus without a log depends on the current time")
    void statusWithoutLog(LocalTime now, OccurrenceStatus expected) {
        var status = AgendaItem.resolveStatus(MONDAY.atTime(8, 0), MONDAY.atTime(9, 0), MONDAY.atTime(now), null);

        assertThat(status).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED,COMPLETED", "CANCELLED,CANCELLED", "MISSED,MISSED"})
    @DisplayName("AgendaItem.resolveStatus returns the logged result even before the start")
    void loggedResultWins(ActivityLogStatus logStatus, OccurrenceStatus expected) {
        var status = AgendaItem.resolveStatus(MONDAY.atTime(8, 0), MONDAY.atTime(9, 0), MONDAY.atTime(7, 0), logStatus);

        assertThat(status).isEqualTo(expected);
    }

    @Test
    @DisplayName("A range crossing midnight belongs to its start date and ends the next day")
    void midnightCrossingBelongsToStartDate() {
        var night = Schedule.oneTime(MONDAY, NIGHT);
        var tuesday = MONDAY.plusDays(1);

        assertThat(night.occursOn(MONDAY)).isTrue();
        assertThat(night.occursOn(tuesday)).isFalse();
        assertThat(night.occurrenceStart(MONDAY)).isEqualTo(LocalDateTime.of(2026, 10, 26, 22, 0));
        assertThat(night.occurrenceEnd(MONDAY)).isEqualTo(LocalDateTime.of(2026, 10, 27, 6, 0));
        assertThat(AgendaItem.resolveStatus(night.occurrenceStart(MONDAY), night.occurrenceEnd(MONDAY),
                tuesday.atTime(2, 0), null)).isEqualTo(OccurrenceStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Schedule rejects fields that do not match its type")
    void scheduleShape() {
        var weekly = new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), AFTERNOON);

        assertThatThrownBy(() -> new Schedule(ScheduleType.ONE_TIME, null, AFTERNOON, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Schedule(ScheduleType.WEEKLY, MONDAY, null, weekly))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Schedule.weekly(weekly).effectiveTimeRange()).isEqualTo(AFTERNOON);
    }

    @Test
    @DisplayName("A weekly assignment occurs on its days and keeps its schedule through persistence columns")
    void weeklyAssignment() {
        var activity = activity();
        var assignment = activity.assign(null, Schedule.weekly(
                new WeeklyPeriod(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), AFTERNOON)), MONDAY);
        ReflectionTestUtils.setField(assignment, "id", 1L);

        assertThat(assignment.isRecurring()).isTrue();
        assertThat(assignment.isAssignedToRelative()).isTrue();
        assertThat(assignment.getSchedule().weeklyPeriod().days())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(activity.occurrencesOn(MONDAY)).containsExactly(assignment);
        assertThat(activity.occurrencesOn(MONDAY.plusDays(1))).isEmpty();
        assertThat(activity.getActivityAssignments().getAssignmentWithId(1L)).isSameAs(assignment);
    }

    @Test
    @DisplayName("CareActivity.assign rejects a past one-time schedule and an inactive activity")
    void assignInvariants() {
        var activity = activity();

        assertThatThrownBy(() -> activity.assign(1L, Schedule.oneTime(MONDAY.minusDays(1), AFTERNOON), MONDAY))
                .isInstanceOf(IllegalArgumentException.class);
        var assignment = activity.assign(1L, Schedule.oneTime(MONDAY, AFTERNOON), MONDAY);
        assertThat(assignment.isAssignedTo(1L)).isTrue();
        assertThat(assignment.isAssignedTo(2L)).isFalse();

        ReflectionTestUtils.setField(activity, "status", ActivityStatus.INACTIVE);
        assertThatThrownBy(() -> activity.assign(1L, Schedule.oneTime(MONDAY, AFTERNOON), MONDAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getAssignmentWithId never returns null")
    void unknownAssignment() {
        assertThatThrownBy(() -> activity().getActivityAssignments().getAssignmentWithId(99L))
                .isInstanceOf(ActivityAssignmentNotFoundException.class);
    }

    @Test
    @DisplayName("GetCareAgendaQuery rejects from after to, missing dates and too long ranges")
    void invalidRanges() {
        assertThatThrownBy(() -> new GetCareAgendaQuery(CAREGIVER, MONDAY, MONDAY.minusDays(1), null, null, null))
                .isInstanceOf(InvalidAgendaRangeException.class);
        assertThatThrownBy(() -> new GetCareAgendaQuery(CAREGIVER, null, MONDAY, null, null, null))
                .isInstanceOf(InvalidAgendaRangeException.class);
        assertThatThrownBy(() -> new GetCareAgendaQuery(CAREGIVER, MONDAY, MONDAY.plusDays(366), null, null, null))
                .isInstanceOf(InvalidAgendaRangeException.class);
        assertThat(new GetCareAgendaQuery(CAREGIVER, MONDAY, MONDAY, null, null, null).from()).isEqualTo(MONDAY);
    }

    private static CareActivity activity() {
        return new CareActivity(new PersonProfileId(8L), CareActivityType.THERAPY, "Therapy", null);
    }
}
