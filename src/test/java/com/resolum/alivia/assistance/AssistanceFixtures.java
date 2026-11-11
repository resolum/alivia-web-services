package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

/** Small builders for Assistance aggregates with database identities already assigned. */
public final class AssistanceFixtures {

    private AssistanceFixtures() {
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static Relative relative(long id) {
        return withId(new Relative(new UserId(id), new PersonProfileId(id), new BusinessId(1L)), id);
    }

    public static AssistedPerson assistedPerson(long id, Relative relative) {
        return withId(new AssistedPerson(new PersonProfileId(100 + id), relative), id);
    }

    public static Invitation invitation(long id, AssistedPerson person, Relative inviter) {
        return withId(new Invitation(person, inviter, new EmailAddress("care@mail.com"),
                LocalDateTime.now().plusDays(2)), id);
    }

    public static Principal principal(String name) {
        return () -> name;
    }

    /** A weekly schedule that runs every day for the whole day. */
    public static Schedule everyDay() {
        return Schedule.weekly(new WeeklyPeriod(EnumSet.allOf(DayOfWeek.class),
                new TimeRange(LocalTime.MIN, LocalTime.of(23, 59, 59))));
    }

    public static Caregiver caregiver(long id, long userId, AssistedPerson person) {
        return withId(new Caregiver(new UserId(userId), person), id);
    }

    /** An active activity with one assignment (id 30) to the given caregiver, or to the relative when null. */
    public static CareActivity activity(long id, AssistedPerson person, Caregiver caregiver) {
        CareActivity activity = withId(new CareActivity(person, CareActivityType.MEDICATION, "Pastilla", "Con agua"), id);
        ActivityAssignment assignment = activity.assign(caregiver, everyDay(), LocalDate.now());
        withId(assignment, 30L);
        return activity;
    }
}
