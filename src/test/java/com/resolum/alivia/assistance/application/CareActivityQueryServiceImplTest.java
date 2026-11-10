package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.queryservices.CareActivityQueryServiceImpl;
import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivitiesByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivityByIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.activity;
import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.caregiver;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static com.resolum.alivia.assistance.AssistanceFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareActivityQueryServiceImplTest {

    private final LocalDate today = LocalDate.now();
    private CareActivityRepository activities;
    private ActivityLogRepository logs;
    private CareActivityQueryServiceImpl service;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        activities = mock(CareActivityRepository.class);
        logs = mock(ActivityLogRepository.class);
        service = new CareActivityQueryServiceImpl(activities, logs);
        person = assistedPerson(2L, relative(1L));
    }

    @Test
    void readsActivitiesByIdAndByAssistedPerson() {
        CareActivity activity = activity(5L, person, null);
        when(activities.findById(5L)).thenReturn(Optional.of(activity));
        when(activities.findAllByAssistedPersonId(2L)).thenReturn(List.of(activity));

        assertThat(service.handle(new GetCareActivityByIdQuery(5L))).contains(activity);
        assertThat(service.handle(new GetCareActivitiesByAssistedPersonIdQuery(2L))).containsExactly(activity);
    }

    @Test
    void buildsTheAgendaOfAnAssistedPersonSkippingInactiveActivities() {
        CareActivity active = activity(5L, person, null);
        CareActivity inactive = activity(6L, person, null);
        inactive.deactivate();
        ActivityLog cancelled = withId(ActivityLog.cancelled(active,
                active.getActivityAssignments().getAssignmentWithId(30L), today, LocalDateTime.now()), 70L);
        when(activities.findAllByAssistedPersonId(2L)).thenReturn(List.of(active, inactive));
        when(logs.findAllByAssistedPersonIdAndDateBetween(2L, today, today.plusDays(1))).thenReturn(List.of(cancelled));

        var agenda = service.handle(new GetAgendaByAssistedPersonIdQuery(2L, today, today.plusDays(1)));

        assertThat(agenda).hasSize(2);
        assertThat(agenda.get(0).status()).isEqualTo(OccurrenceStatus.CANCELLED);
        assertThat(agenda.get(1).status()).isEqualTo(OccurrenceStatus.SCHEDULED);
    }

    @Test
    void buildsTheAgendaOfACaregiverWithOnlyTheirAssignments() {
        Caregiver caregiver = caregiver(3L, 9L, person);
        Caregiver other = caregiver(4L, 10L, person);
        CareActivity mine = activity(5L, person, caregiver);
        CareActivity theirs = activity(6L, person, other);
        CareActivity relatives = activity(7L, person, null);
        when(activities.findAllActive()).thenReturn(List.of(mine, theirs, relatives));
        when(logs.findAllByAssignedCaregiverIdAndDateBetween(3L, today.minusDays(1), today)).thenReturn(List.of());

        var agenda = service.handle(new GetAgendaByCaregiverIdQuery(3L, today.minusDays(1), today));

        assertThat(agenda).hasSize(2).allMatch(item -> item.caregiverId().equals(3L));
        assertThat(agenda.get(0).status()).isEqualTo(OccurrenceStatus.MISSED);
    }
}
