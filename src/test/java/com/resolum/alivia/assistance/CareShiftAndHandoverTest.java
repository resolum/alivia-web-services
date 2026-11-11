package com.resolum.alivia.assistance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.assistance.application.internal.commandservices.CareShiftCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.HandoverCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.CareShiftQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.HandoverQueryServiceImpl;
import com.resolum.alivia.assistance.domain.exceptions.CareShiftNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotPendingException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftConflictException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftHasPendingHandoverException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AnswerHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.AssignShiftCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeleteShiftCommand;
import com.resolum.alivia.assistance.domain.model.commands.EscalateHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.ProposeSubstituteCommand;
import com.resolum.alivia.assistance.domain.model.commands.RequestHandoverCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoversQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftsQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareShiftStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.HandoverStatus;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CareShiftAndHandoverTest {

    @Mock
    private CareShiftRepository careShiftRepository;

    @Mock
    private AssistedPersonRepository assistedPersonRepository;

    @Mock
    private HandoverRepository handoverRepository;

    @Mock
    private RelativeRepository relativeRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    private CareShiftCommandServiceImpl shiftCommandService;
    private CareShiftQueryServiceImpl shiftQueryService;
    private HandoverCommandServiceImpl handoverCommandService;
    private HandoverQueryServiceImpl handoverQueryService;

    private AssistedPerson samplePerson;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        shiftCommandService = new CareShiftCommandServiceImpl(
                careShiftRepository, assistedPersonRepository, handoverRepository, relativeRepository,
                caregiverRepository);
        shiftQueryService = new CareShiftQueryServiceImpl(
                careShiftRepository, assistedPersonRepository, relativeRepository, caregiverRepository);
        handoverCommandService = new HandoverCommandServiceImpl(handoverRepository, careShiftRepository);
        handoverQueryService = new HandoverQueryServiceImpl(
                handoverRepository, assistedPersonRepository, relativeRepository, caregiverRepository);

        Relative relative = new Relative(new UserId(1L), new PersonProfileId(10L), new BusinessId(100L));
        samplePerson = new AssistedPerson(new PersonProfileId(20L), relative);
        ReflectionTestUtils.setField(samplePerson, "id", 1L);
    }

    @Test
    @DisplayName("CareShift overlaps check works correctly")
    void testCareShiftOverlaps() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 16, 0);
        CareShift shift = new CareShift(samplePerson, 2L, "Maria Perez", start, end, "Notas");

        assertThat(shift.overlaps(start.plusHours(1), end.minusHours(1))).isTrue();
        assertThat(shift.overlaps(start.minusHours(2), start.minusHours(1))).isFalse();
        assertThat(shift.overlaps(end.plusHours(1), end.plusHours(2))).isFalse();
        assertThat(shift.getStatus()).isEqualTo(CareShiftStatus.SCHEDULED);
    }

    @Test
    @DisplayName("Assign shift successfully creates a shift when no conflict")
    void testAssignShiftSuccess() {
        when(assistedPersonRepository.findById(1L)).thenReturn(Optional.of(samplePerson));
        when(careShiftRepository.findConflictingShifts(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(careShiftRepository.save(any(CareShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 16, 0);
        AssignShiftCommand command = new AssignShiftCommand(
                null, 1L, 2L, "Maria Perez", start, end, "Notas", false);

        CareShift result = shiftCommandService.handle(command);
        assertThat(result).isNotNull();
        assertThat(result.getCaregiverName()).isEqualTo("Maria Perez");
        assertThat(result.getCaregiverId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Assign shift throws ShiftConflictException when conflict exists and allowConflict is false")
    void testAssignShiftConflict() {
        when(assistedPersonRepository.findById(1L)).thenReturn(Optional.of(samplePerson));
        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 16, 0);
        CareShift existing = new CareShift(samplePerson, 3L, "Carlos", start, end, "Existente");
        when(careShiftRepository.findConflictingShifts(1L, start, end, null)).thenReturn(List.of(existing));

        AssignShiftCommand command = new AssignShiftCommand(
                null, 1L, 2L, "Maria Perez", start, end, "Notas", false);

        assertThatThrownBy(() -> shiftCommandService.handle(command))
                .isInstanceOf(ShiftConflictException.class);
    }

    @Test
    @DisplayName("Delete shift fails when open handover exists")
    void testDeleteShiftWithPendingHandover() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 16, 0);
        CareShift shift = new CareShift(samplePerson, 2L, "Maria", start, end, "");
        ReflectionTestUtils.setField(shift, "id", 100L);

        when(careShiftRepository.findById(100L)).thenReturn(Optional.of(shift));
        Handover openHandover = mock(Handover.class);
        when(handoverRepository.findOpenHandoversByShiftId(100L)).thenReturn(List.of(openHandover));

        assertThatThrownBy(() -> shiftCommandService.handle(new DeleteShiftCommand(100L)))
                .isInstanceOf(ShiftHasPendingHandoverException.class);
    }

    @Test
    @DisplayName("Handover request is idempotent and returns existing handover if already requested")
    void testRequestHandoverIdempotent() {
        Handover existing = mock(Handover.class);
        when(handoverRepository.findByFromShiftId(10L)).thenReturn(Optional.of(existing));

        Handover result = handoverCommandService
                .handle(new RequestHandoverCommand(10L, 20L, List.of("T1"), List.of("T2")));
        assertThat(result).isSameAs(existing);
        verify(careShiftRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Handover accept transitions status and updates shift when substitute accepted")
    void testAnswerHandoverWithSubstitute() {
        LocalDateTime start1 = LocalDateTime.of(2026, 11, 9, 8, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 11, 9, 16, 0);
        CareShift fromShift = new CareShift(samplePerson, 1L, "Saliente", start1, end1, "");
        ReflectionTestUtils.setField(fromShift, "id", 10L);

        LocalDateTime start2 = LocalDateTime.of(2026, 11, 9, 16, 0);
        LocalDateTime end2 = LocalDateTime.of(2026, 11, 9, 23, 0);
        CareShift toShift = new CareShift(samplePerson, 2L, "Entrante", start2, end2, "");
        ReflectionTestUtils.setField(toShift, "id", 20L);

        Handover handover = new Handover(fromShift, toShift, "[]", "[]");
        ReflectionTestUtils.setField(handover, "id", 100L);
        handover.proposeSubstitute(3L, "Sustituto");

        when(handoverRepository.findById(100L)).thenReturn(Optional.of(handover));
        when(handoverRepository.save(any(Handover.class))).thenAnswer(i -> i.getArgument(0));

        AnswerHandoverCommand answer = new AnswerHandoverCommand(
                100L, true, 3L, "Sustituto", List.of("Tarea 1"), List.of("Tarea 2"));

        Handover answered = handoverCommandService.handle(answer);
        assertThat(answered.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        assertThat(toShift.getCaregiverId()).isEqualTo(3L);
        assertThat(toShift.getCaregiverName()).isEqualTo("Sustituto");
    }

    @Test
    @DisplayName("Handover escalate sets escalatedAt timestamp")
    void testEscalateHandover() {
        CareShift fromShift = mock(CareShift.class);
        CareShift toShift = mock(CareShift.class);
        when(fromShift.getCaregiverId()).thenReturn(1L);
        when(fromShift.getCaregiverName()).thenReturn("Saliente");
        when(toShift.getCaregiverId()).thenReturn(2L);
        when(toShift.getCaregiverName()).thenReturn("Entrante");

        Handover handover = new Handover(fromShift, toShift, "[]", "[]");
        ReflectionTestUtils.setField(handover, "id", 200L);
        when(handoverRepository.findById(200L)).thenReturn(Optional.of(handover));
        when(handoverRepository.save(any(Handover.class))).thenAnswer(i -> i.getArgument(0));

        Handover escalated = handoverCommandService.handle(new EscalateHandoverCommand(200L));
        assertThat(escalated.getEscalatedAt()).isNotNull();
    }
}
