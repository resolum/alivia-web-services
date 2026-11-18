package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.AssistanceFixtures;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.assistance.application.internal.tasks.CareActivityReminderScheduler;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareActivityReminderSchedulerTest {
    /** 2026-11-18 07:50 in Lima. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T12:50:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private final CareActivityRepository activities = mock(CareActivityRepository.class);
    private final NotificationsContextFacade notifications = mock(NotificationsContextFacade.class);

    private CareActivityReminderScheduler scheduler(Clock clock) {
        return new CareActivityReminderScheduler(activities, new ExternalNotificationsService(notifications),
                TransactionOperations.withoutTransaction(), clock, "America/Lima", 15);
    }

    @Test
    void remindsTheRelativeOfTheActivitiesAboutToStart() {
        var relative = AssistanceFixtures.relative(40L);
        var person = AssistanceFixtures.assistedPerson(1L, relative);
        var medicine = activity(person, CareActivityType.MEDICATION, "Losartán 50 mg", 11L, TODAY, 8, 0);
        var walk = activity(person, CareActivityType.EXERCISE, "Paseo", 12L, TODAY, 7, 55);
        var later = activity(person, CareActivityType.MEAL, "Almuerzo", 13L, TODAY, 12, 0);
        var earlier = activity(person, CareActivityType.MEAL, "Desayuno", 14L, TODAY, 7, 30);
        when(activities.findAllActive()).thenReturn(List.of(medicine, walk, later, earlier));
        when(notifications.notifyUserOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        assertEquals(2, scheduler(CLOCK).remindDueActivities());

        verify(notifications).notifyUserOnce(40L, "CARE_ACTIVITY_REMINDER", "activity-11-2026-11-18",
                "Hora del medicamento", "Losartán 50 mg a las 08:00.");
        verify(notifications).notifyUserOnce(40L, "CARE_ACTIVITY_REMINDER", "activity-12-2026-11-18",
                "Recordatorio de actividad", "Paseo a las 07:55.");
    }

    @Test
    void looksIntoTheNextDayNearMidnightAndCountsOnlyNewReminders() {
        var person = AssistanceFixtures.assistedPerson(1L, AssistanceFixtures.relative(40L));
        var night = activity(person, CareActivityType.MEDICATION, "Insulina", 21L, TODAY.plusDays(1), 0, 5);
        when(activities.findAllActive()).thenReturn(List.of(night));
        when(notifications.notifyUserOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        var lateNight = Clock.fixed(Instant.parse("2026-11-19T04:55:00Z"), ZoneOffset.UTC);

        assertEquals(0, scheduler(lateNight).remindDueActivities());

        verify(notifications).notifyUserOnce(40L, "CARE_ACTIVITY_REMINDER", "activity-21-2026-11-19",
                "Hora del medicamento", "Insulina a las 00:05.");
    }

    @Test
    void aFailureNeitherStopsTheOtherRemindersNorTheTask() {
        var person = AssistanceFixtures.assistedPerson(1L, AssistanceFixtures.relative(40L));
        var first = activity(person, CareActivityType.OTHER, "Uno", 31L, TODAY, 8, 0);
        var second = activity(person, CareActivityType.OTHER, "Dos", 32L, TODAY, 8, 1);
        when(activities.findAllActive()).thenReturn(List.of(first, second));
        when(notifications.notifyUserOnce(40L, "CARE_ACTIVITY_REMINDER", "activity-31-2026-11-18", "Recordatorio de actividad",
                "Uno a las 08:00.")).thenThrow(new IllegalStateException("down"));
        when(notifications.notifyUserOnce(40L, "CARE_ACTIVITY_REMINDER", "activity-32-2026-11-18", "Recordatorio de actividad",
                "Dos a las 08:01.")).thenReturn(true);

        assertEquals(1, scheduler(CLOCK).remindDueActivities());

        when(activities.findAllActive()).thenThrow(new IllegalStateException("database down"));
        scheduler(CLOCK).run();
    }

    @Test
    void skipsActivitiesWithoutRelative() {
        var person = AssistanceFixtures.assistedPerson(1L, AssistanceFixtures.relative(40L));
        var activity = activity(person, CareActivityType.OTHER, "Uno", 41L, TODAY, 8, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(person, "relative", null);
        when(activities.findAllActive()).thenReturn(List.of(activity));

        assertEquals(0, scheduler(CLOCK).remindDueActivities());
        verify(notifications, never()).notifyUserOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    private static CareActivity activity(com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson person,
                                         CareActivityType type, String title, long assignmentId, LocalDate date,
                                         int hour, int minute) {
        var activity = new CareActivity(person, type, title, null);
        var start = LocalTime.of(hour, minute);
        var assignment = activity.assign(null, Schedule.oneTime(date, new TimeRange(start, start.plusMinutes(30))), date);
        AssistanceFixtures.withId(assignment, assignmentId);
        return activity;
    }
}
