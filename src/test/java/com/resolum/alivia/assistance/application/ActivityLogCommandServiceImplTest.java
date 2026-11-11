package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.commandservices.ActivityLogCommandServiceImpl;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceClosedException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.MarkMissedActivitiesCommand;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.activity;
import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.caregiver;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static com.resolum.alivia.assistance.AssistanceFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityLogCommandServiceImplTest {

    private final LocalDate today = LocalDate.now();
    private ActivityLogRepository logs;
    private CareActivityRepository activities;
    private RelativeRepository relatives;
    private CaregiverRepository caregivers;
    private ActivityLogCommandServiceImpl service;
    private Relative relative;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        logs = mock(ActivityLogRepository.class);
        activities = mock(CareActivityRepository.class);
        relatives = mock(RelativeRepository.class);
        caregivers = mock(CaregiverRepository.class);
        service = new ActivityLogCommandServiceImpl(logs, activities, relatives, caregivers);
        relative = relative(1L);
        person = assistedPerson(2L, relative);
        when(logs.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 77L));
    }

    @Test
    void completesAnOccurrenceAssignedToTheRelative() {
        when(activities.findById(5L)).thenReturn(Optional.of(activity(5L, person, null)));

        Long id = service.handle(new CompleteActivityCommand(5L, 30L, today, null, " hecho "));

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<ActivityLog> saved = ArgumentCaptor.forClass(ActivityLog.class);
        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ActivityLogStatus.COMPLETED);
    }

    @Test
    void completesAnOccurrenceAsTheAssignedCaregiver() {
        Caregiver caregiver = caregiver(3L, 9L, person);
        when(activities.findById(5L)).thenReturn(Optional.of(activity(5L, person, caregiver)));
        when(caregivers.findByAssistedPersonIdAndUserIdAndStatus(2L, new UserId(9L), CaregiverStatus.ACTIVE))
                .thenReturn(Optional.of(caregiver));

        assertThat(service.handle(new CompleteActivityCommand(5L, 30L, today, 9L, null))).isEqualTo(77L);
    }

    @Test
    void completeRejectsMissingActivitiesAndClosedOccurrences() {
        when(activities.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.handle(new CompleteActivityCommand(5L, 30L, today, null, null)))
                .isInstanceOf(CareActivityNotFoundException.class);

        when(activities.findById(6L)).thenReturn(Optional.of(activity(6L, person, null)));
        when(logs.existsByAssignmentIdAndDate(30L, today)).thenReturn(true);
        assertThatThrownBy(() -> service.handle(new CompleteActivityCommand(6L, 30L, today, null, null)))
                .isInstanceOf(OccurrenceClosedException.class);
    }

    @Test
    void cancelsAnOccurrenceForTheManagingRelative() {
        when(activities.findById(5L)).thenReturn(Optional.of(activity(5L, person, null)));
        when(relatives.findById(1L)).thenReturn(Optional.of(relative));

        assertThat(service.handle(new CancelActivityOccurrenceCommand(5L, 30L, today, 1L))).isEqualTo(77L);
    }

    @Test
    void cancelRejectsEveryInvalidCase() {
        when(activities.findById(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.handle(new CancelActivityOccurrenceCommand(4L, 30L, today, 1L)))
                .isInstanceOf(CareActivityNotFoundException.class);

        when(activities.findById(5L)).thenReturn(Optional.of(activity(5L, person, null)));
        when(relatives.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.handle(new CancelActivityOccurrenceCommand(5L, 30L, today, 8L)))
                .isInstanceOf(RelativeNotFoundException.class);

        when(relatives.findById(9L)).thenReturn(Optional.of(relative(9L)));
        assertThatThrownBy(() -> service.handle(new CancelActivityOccurrenceCommand(5L, 30L, today, 9L)))
                .isInstanceOf(AssistedPersonNotManagedByRelativeException.class);

        when(relatives.findById(1L)).thenReturn(Optional.of(relative));
        when(logs.existsByAssignmentIdAndDate(30L, today)).thenReturn(true);
        assertThatThrownBy(() -> service.handle(new CancelActivityOccurrenceCommand(5L, 30L, today, 1L)))
                .isInstanceOf(OccurrenceClosedException.class);
    }

    @Test
    void marksOnlyOpenActiveOccurrencesAsMissed() {
        CareActivity open = activity(5L, person, null);
        CareActivity inactiveAssignment = activity(6L, person, null);
        inactiveAssignment.deactivateAssignment(30L);
        CareActivity logged = activity(7L, person, null);
        withId(logged.getActivityAssignments().getActivityAssignments().get(0), 31L);
        when(activities.findAllActive()).thenReturn(List.of(open, inactiveAssignment, logged));
        when(logs.existsByAssignmentIdAndDate(31L, today.minusDays(1))).thenReturn(true);

        service.handle(new MarkMissedActivitiesCommand(today.minusDays(1)));

        verify(logs, times(1)).save(any());
    }

    @Test
    void marksMissedOccurrencesOfAnEarlierClosingDate() {
        CareActivity activity = activity(5L, person, null);
        when(activities.findAllActive()).thenReturn(List.of(activity));
        when(logs.existsByAssignmentIdAndDate(anyLong(), any())).thenReturn(false);

        service.handle(new MarkMissedActivitiesCommand(today.minusDays(3)));

        verify(logs, times(1)).save(any());
        verify(caregivers, never()).findByAssistedPersonIdAndUserIdAndStatus(any(), any(), any());
    }
}
