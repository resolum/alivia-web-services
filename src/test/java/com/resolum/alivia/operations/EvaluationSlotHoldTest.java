package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.commandservices.AppointmentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.commandservices.EvaluationSlotHoldCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.domain.exceptions.AppointmentAlreadyExists;
import com.resolum.alivia.operations.domain.exceptions.AppointmentSlotTaken;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.EvaluationSlotHold;
import com.resolum.alivia.operations.domain.model.commands.HoldEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.commands.ReleaseEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalAvailabilityQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A family holds the window and technician it picked while it pays, and other families stop seeing it. */
class EvaluationSlotHoldTest {
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 29);
    private static final TimeRange TEN = range(10);
    private static final TechnicianAvailability ANA = new TechnicianAvailability(7L, "Ana Pérez", List.of(range(8, 12)));
    private static final TechnicianAvailability LUIS = new TechnicianAvailability(8L, "Luis Rojas", List.of(range(8, 12)));

    private AppointmentRepository appointments;
    private EvaluationSlotHoldRepository holds;
    private ExternalHcmService hcm;
    private ExternalSubscriptionsService subscriptions;
    private EvaluationSlotHoldCommandServiceImpl holdService;
    private AppointmentQueryServiceImpl queries;
    private AppointmentCommandServiceImpl commands;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        holds = mock(EvaluationSlotHoldRepository.class);
        hcm = mock(ExternalHcmService.class);
        subscriptions = mock(ExternalSubscriptionsService.class);
        holdService = new EvaluationSlotHoldCommandServiceImpl(holds, appointments, hcm, CLOCK);
        queries = new AppointmentQueryServiceImpl(appointments, holds, hcm, CLOCK);
        commands = new AppointmentCommandServiceImpl(appointments, holds, mock(IncidentPersistenceRepository.class), hcm,
                subscriptions, CLOCK);
        when(hcm.fetchTechniciansWorkingOn(any())).thenReturn(List.of());
        when(hcm.fetchTechniciansWorkingOn(TOMORROW)).thenReturn(List.of(ANA, LUIS));
        when(appointments.findByScheduledDateAndStatus(any(), eq(AppointmentStatus.SCHEDULED))).thenReturn(List.of());
        when(holds.findByScheduledDateAndExpiresAtAfter(any(), any())).thenReturn(List.of());
        when(holds.findByDraftId(any())).thenReturn(Optional.empty());
        when(holds.saveAndFlush(any(EvaluationSlotHold.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void holdLastsThirtyMinutesAndMovesWithTheChoice() {
        var hold = new EvaluationSlotHold("d1", TOMORROW, TEN, 7L, "Ana Pérez", NOW);

        assertEquals(NOW.plusSeconds(1800), hold.getExpiresAt());
        assertTrue(hold.isActive(NOW.plusSeconds(1799)));
        assertFalse(hold.isActive(NOW.plusSeconds(1800)));
        assertTrue(hold.isAttendedBy(7L));
        assertTrue(hold.holds(TOMORROW, TEN, 7L));
        assertFalse(hold.holds(TOMORROW, TEN, 8L));
        assertFalse(hold.holds(TOMORROW.plusDays(1), TEN, 7L));
        assertFalse(hold.holds(TOMORROW, range(8), 7L));

        hold.moveTo(TOMORROW, range(8), 8L, "Luis Rojas", NOW.plusSeconds(600));
        assertEquals(LocalTime.of(8, 0), hold.getStartTime());
        assertEquals("Luis Rojas", hold.getTechnicianName());
        assertEquals(NOW.plusSeconds(2400), hold.getExpiresAt());
        assertThrows(IllegalArgumentException.class, () -> new EvaluationSlotHold(" ", TOMORROW, TEN, 7L, "Ana", NOW));
        assertThrows(IllegalArgumentException.class, () -> hold.moveTo(null, TEN, 7L, "Ana", NOW));
        assertThrows(IllegalArgumentException.class, () -> hold.moveTo(TOMORROW, TEN, null, "Ana", NOW));
    }

    @Test
    void holdsAFreeWindowWithTheChosenTechnician() {
        var hold = holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), 8L));

        assertEquals("Luis Rojas", hold.getTechnicianName());
        assertEquals(NOW.plus(EvaluationSlotHold.DURATION), hold.getExpiresAt());
        verify(holds).deleteExpired(NOW);
    }

    @Test
    void renewingTheOwnHoldMovesItInsteadOfAddingAnother() {
        var current = new EvaluationSlotHold("d1", TOMORROW, range(8), 7L, "Ana Pérez", NOW.minusSeconds(900));
        when(holds.findByDraftId("d1")).thenReturn(Optional.of(current));
        when(holds.findByScheduledDateAndExpiresAtAfter(TOMORROW, NOW)).thenReturn(List.of(current));

        var hold = holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), 7L));

        assertSame(current, hold);
        assertEquals(LocalTime.of(10, 0), hold.getStartTime());
        assertEquals(NOW.plus(EvaluationSlotHold.DURATION), hold.getExpiresAt());
    }

    @Test
    void refusesATechnicianAnotherFamilyIsHolding() {
        when(holds.findByScheduledDateAndExpiresAtAfter(TOMORROW, NOW))
                .thenReturn(List.of(new EvaluationSlotHold("d2", TOMORROW, TEN, 7L, "Ana Pérez", NOW)));

        assertThrows(AppointmentSlotTaken.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), 7L)));
        // Luis is still free at 10:00.
        assertEquals(8L, holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), 8L))
                .getTechnicianId());
    }

    @Test
    void turnsASimultaneousHoldIntoATakenWindow() {
        when(holds.saveAndFlush(any(EvaluationSlotHold.class))).thenThrow(new DataIntegrityViolationException("uk"));

        assertThrows(AppointmentSlotTaken.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), 7L)));
    }

    @Test
    void validatesTheHoldRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand(" ", TOMORROW, TEN.start(), TEN.end(), 7L)));
        assertThrows(IllegalArgumentException.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", null, TEN.start(), TEN.end(), 7L)));
        assertThrows(IllegalArgumentException.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), null)));
        assertThrows(IllegalArgumentException.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW.minusDays(1), TEN.start(), TEN.end(), 7L)));
        assertThrows(IllegalArgumentException.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW.plusDays(7), TEN.start(), TEN.end(), 7L)));
        when(appointments.existsByDraftIdAndServiceTypeAndStatus("d1", ServiceType.EVALUATION, AppointmentStatus.SCHEDULED))
                .thenReturn(true);
        assertThrows(AppointmentAlreadyExists.class,
                () -> holdService.handle(new HoldEvaluationSlotCommand("d1", TOMORROW, TEN.start(), TEN.end(), 7L)));
    }

    @Test
    void releasingGivesTheWindowBack() {
        holdService.handle(new ReleaseEvaluationSlotCommand("d1"));
        holdService.handle(new ReleaseEvaluationSlotCommand(null));
        holdService.handle(new ReleaseEvaluationSlotCommand(""));

        verify(holds, times(1)).deleteByDraft(any());
        verify(holds).deleteByDraft("d1");
    }

    @Test
    void otherFamiliesStopSeeingAHeldTechnicianButTheHolderStillDoes() {
        when(holds.findByScheduledDateAndExpiresAtAfter(TOMORROW, NOW))
                .thenReturn(List.of(new EvaluationSlotHold("d2", TOMORROW, TEN, 7L, "Ana Pérez", NOW)));

        var forOthers = queries.handle(new GetAvailableSlotsQuery(TOMORROW, 1, "d1"));
        var forHolder = queries.handle(new GetAvailableSlotsQuery(TOMORROW, 1, "d2"));

        assertFalse(forOthers.stream().anyMatch(slot -> slot.window().equals(TEN) && slot.technicianId() == 7L));
        assertTrue(forOthers.stream().anyMatch(slot -> slot.window().equals(TEN) && slot.technicianId() == 8L));
        assertTrue(forHolder.stream().anyMatch(slot -> slot.window().equals(TEN) && slot.technicianId() == 7L));
        assertEquals(4, forHolder.size());
        assertEquals(3, forOthers.size());
        // The capacity the admin sees also counts the hold.
        var blocks = queries.handle(new GetTechnicalAvailabilityQuery(ServiceType.EVALUATION, TOMORROW, 1));
        assertEquals(1, blocks.stream().filter(block -> block.slotId().window().equals(TEN)).findFirst().orElseThrow().capacity());
    }

    @Test
    void bookingUsesTheOwnHoldAndConsumesIt() {
        when(subscriptions.isReadyForEvaluation(any())).thenReturn(true);
        when(holds.findByScheduledDateAndExpiresAtAfter(TOMORROW, NOW))
                .thenReturn(List.of(new EvaluationSlotHold("d1", TOMORROW, TEN, 7L, "Ana Pérez", NOW)));

        assertThrows(AppointmentSlotTaken.class, () -> commands.handle(schedule("d2", 7L)));
        var appointment = commands.handle(schedule("d1", 7L));

        assertEquals("Ana Pérez", appointment.getTechnicianName());
        verify(holds).deleteByDraft("d1");
        verify(holds, never()).deleteByDraft("d2");
    }

    private static ScheduleAppointmentCommand schedule(String draftId, Long technicianId) {
        return new ScheduleAppointmentCommand(draftId, TOMORROW, TEN.start(), TEN.end(), technicianId, null, null, null);
    }

    private static TimeRange range(int from) {
        return range(from, from + 2);
    }

    private static TimeRange range(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
