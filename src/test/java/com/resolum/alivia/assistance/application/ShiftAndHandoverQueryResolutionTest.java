package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.commandservices.CareShiftCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.CareShiftQueryServiceImpl;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareShiftNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftHasPendingHandoverException;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.commands.AssignShiftCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeleteShiftCommand;
import com.resolum.alivia.assistance.application.internal.queryservices.HandoverQueryServiceImpl;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoverByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoversQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftsQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.caregiver;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The shift services and handover queries find the assisted person from the id, the caller or the first person. */
class ShiftAndHandoverQueryResolutionTest {

    private CareShiftRepository shifts;
    private HandoverRepository handovers;
    private AssistedPersonRepository persons;
    private RelativeRepository relatives;
    private CaregiverRepository caregivers;
    private CareShiftQueryServiceImpl shiftQueries;
    private HandoverQueryServiceImpl handoverQueries;
    private CareShiftCommandServiceImpl shiftCommands;
    private Relative relative;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        shifts = mock(CareShiftRepository.class);
        handovers = mock(HandoverRepository.class);
        persons = mock(AssistedPersonRepository.class);
        relatives = mock(RelativeRepository.class);
        caregivers = mock(CaregiverRepository.class);
        shiftQueries = new CareShiftQueryServiceImpl(shifts, persons, relatives, caregivers);
        handoverQueries = new HandoverQueryServiceImpl(handovers, persons, relatives, caregivers);
        shiftCommands = new CareShiftCommandServiceImpl(shifts, persons, handovers, relatives, caregivers);
        when(shifts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        relative = relative(1L);
        person = assistedPerson(2L, relative);
    }

    private void resolvesTo(UserId userId, long expected) {
        shiftQueries.handle(new GetShiftsQuery(null, userId, null, null));
        verify(shifts).findByAssistedPersonId(expected);
        handoverQueries.handle(new GetHandoversQuery(null, userId));
        verify(handovers).findByAssistedPersonId(expected);
        AssistedPerson target = assistedPerson(expected, relative);
        when(persons.findById(expected)).thenReturn(Optional.of(target));
        CareShift shift = shiftCommands.handle(assign(null, null, userId));
        assertThat(shift.getAssistedPerson()).isSameAs(target);
    }

    private static AssignShiftCommand assign(Long shiftId, Long assistedPersonId, UserId userId) {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 8, 0);
        return new AssignShiftCommand(shiftId, assistedPersonId, userId, 3L, "Carla", start, start.plusHours(8),
                "Turno", true);
    }

    @Test
    void usesTheGivenAssistedPersonAndRange() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 10, 0, 0);
        shiftQueries.handle(new GetShiftsQuery(2L, null, from, from.plusDays(1)));
        verify(shifts).findByAssistedPersonIdAndRange(2L, from, from.plusDays(1));
        shiftQueries.handle(new GetShiftsQuery(2L, null, from, null));
        verify(shifts).findByAssistedPersonId(2L);
        handoverQueries.handle(new GetHandoversQuery(2L, null));
        verify(handovers).findByAssistedPersonId(2L);
    }

    @Test
    void usesTheFirstPersonOfARelativeCaller() {
        when(relatives.findByUserId(new UserId(1L))).thenReturn(Optional.of(relative));
        when(persons.findAllByRelativeId(1L)).thenReturn(List.of(person));
        resolvesTo(new UserId(1L), 2L);
    }

    @Test
    void usesThePersonOfACaregiverCaller() {
        when(relatives.findByUserId(new UserId(9L))).thenReturn(Optional.empty());
        when(caregivers.findAllByUserId(new UserId(9L))).thenReturn(List.of(caregiver(3L, 9L, person)));
        resolvesTo(new UserId(9L), 2L);
    }

    @Test
    void fallsBackToTheFirstPersonWhenTheCallerHasNone() {
        AssistedPerson first = assistedPerson(5L, relative);
        Caregiver orphan = caregiver(3L, 9L, person);
        ReflectionTestUtils.setField(orphan, "assistedPerson", null);
        when(relatives.findByUserId(new UserId(1L))).thenReturn(Optional.of(relative));
        when(persons.findAllByRelativeId(1L)).thenReturn(List.of());
        when(relatives.findByUserId(new UserId(9L))).thenReturn(Optional.empty());
        when(caregivers.findAllByUserId(new UserId(9L))).thenReturn(List.of(orphan));
        when(persons.findAll()).thenReturn(List.of(first));

        shiftQueries.handle(new GetShiftsQuery(null, new UserId(1L), null, null));
        shiftQueries.handle(new GetShiftsQuery(null, new UserId(9L), null, null));
        shiftQueries.handle(new GetShiftsQuery(null, null, null, null));
        handoverQueries.handle(new GetHandoversQuery(null, new UserId(1L)));
        handoverQueries.handle(new GetHandoversQuery(null, new UserId(9L)));
        handoverQueries.handle(new GetHandoversQuery(null, null));

        verify(shifts, org.mockito.Mockito.times(3)).findByAssistedPersonId(5L);
        verify(handovers, org.mockito.Mockito.times(3)).findByAssistedPersonId(5L);
    }

    @Test
    void usesTheDefaultIdWithoutAnyPerson() {
        when(persons.findAll()).thenReturn(List.of());
        when(caregivers.findAllByUserId(new UserId(4L))).thenReturn(List.of());
        resolvesTo(new UserId(4L), 1L);
    }

    @Test
    void readsOneShiftAndOneHandover() {
        when(shifts.findById(3L)).thenReturn(Optional.empty());
        when(handovers.findById(4L)).thenReturn(Optional.empty());

        assertThat(shiftQueries.handle(new GetShiftByIdQuery(3L))).isEmpty();
        assertThat(handoverQueries.handle(new GetHandoverByIdQuery(4L))).isEmpty();
    }

    @Test
    void assignRejectsAnUnknownPersonOrShift() {
        when(persons.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> shiftCommands.handle(assign(null, 2L, null)))
                .isInstanceOf(AssistedPersonNotFoundException.class);

        when(persons.findById(5L)).thenReturn(Optional.of(person));
        when(shifts.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> shiftCommands.handle(assign(7L, 5L, null)))
                .isInstanceOf(CareShiftNotFoundException.class);
    }

    @Test
    void deletesAShiftOnlyWithoutOpenHandovers() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 8, 0);
        CareShift shift = new CareShift(person, 3L, "Carla", start, start.plusHours(8), "");
        CareShift next = new CareShift(person, 4L, "Luis", start.plusHours(8), start.plusHours(16), "");
        when(shifts.findById(7L)).thenReturn(Optional.of(shift));
        when(handovers.findOpenHandoversByShiftId(7L)).thenReturn(List.of());
        shiftCommands.handle(new DeleteShiftCommand(7L));
        verify(shifts).delete(shift);

        when(shifts.findById(8L)).thenReturn(Optional.of(next));
        when(handovers.findOpenHandoversByShiftId(8L)).thenReturn(List.of(new Handover(shift, next, "[]", "[]")));
        assertThatThrownBy(() -> shiftCommands.handle(new DeleteShiftCommand(8L)))
                .isInstanceOf(ShiftHasPendingHandoverException.class);

        when(shifts.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> shiftCommands.handle(new DeleteShiftCommand(9L)))
                .isInstanceOf(CareShiftNotFoundException.class);
    }
}
