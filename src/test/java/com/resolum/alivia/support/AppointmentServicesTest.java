package com.resolum.alivia.support;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.support.application.internal.commandservices.AppointmentCommandServiceImpl;
import com.resolum.alivia.support.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.support.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.support.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.support.domain.exceptions.AppointmentAlreadyExists;
import com.resolum.alivia.support.domain.exceptions.AppointmentSlotTaken;
import com.resolum.alivia.support.domain.exceptions.DraftNotReadyForAppointment;
import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.support.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.support.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.support.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.support.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentServicesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 29);
    private static final TechnicianAvailability ANA =
            new TechnicianAvailability(7L, "Ana Pérez", List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0))));

    private AppointmentRepository appointments;
    private ExternalHcmService hcm;
    private ExternalSubscriptionsService subscriptions;
    private AppointmentCommandServiceImpl commands;
    private AppointmentQueryServiceImpl queries;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        hcm = mock(ExternalHcmService.class);
        subscriptions = mock(ExternalSubscriptionsService.class);
        commands = new AppointmentCommandServiceImpl(appointments, hcm, subscriptions, CLOCK);
        queries = new AppointmentQueryServiceImpl(appointments, hcm, CLOCK);
        when(hcm.fetchTechniciansWorkingOn(any())).thenReturn(List.of());
        when(hcm.fetchTechniciansWorkingOn(TOMORROW)).thenReturn(List.of(ANA));
        when(appointments.findByScheduledDateAndStatus(any(), eq(AppointmentStatus.SCHEDULED))).thenReturn(List.of());
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsWindowsFromTomorrowWithinTheWeek() {
        var slots = queries.handle(new GetAvailableSlotsQuery(LocalDate.of(2026, 10, 1), 30));

        assertEquals(2, slots.size());
        assertEquals(TOMORROW, slots.getFirst().date());
        verify(hcm, times(7)).fetchTechniciansWorkingOn(any());
        verify(hcm, never()).fetchTechniciansWorkingOn(TOMORROW.plusDays(7));
        queries.handle(new GetAvailableSlotsQuery(null, 0));
        verify(hcm, times(2)).fetchTechniciansWorkingOn(TOMORROW);
    }

    @Test
    void schedulesWithTheTechnicianOfTheWindow() {
        when(subscriptions.isReadyForEvaluation("draft")).thenReturn(true);

        var appointment = commands.handle(command(TOMORROW, 10, 7L));

        assertEquals("Ana Pérez", appointment.getTechnicianName());
        assertEquals(LocalTime.of(10, 0), appointment.getStartTime());
    }

    @Test
    void rejectsDraftsWithoutPaymentOrAlreadyScheduled() {
        assertThrows(DraftNotReadyForAppointment.class, () -> commands.handle(command(TOMORROW, 10, 7L)));
        when(subscriptions.isReadyForEvaluation("draft")).thenReturn(true);
        when(appointments.existsByDraftIdAndStatus("draft", AppointmentStatus.SCHEDULED)).thenReturn(true);
        assertThrows(AppointmentAlreadyExists.class, () -> commands.handle(command(TOMORROW, 10, 7L)));
    }

    @Test
    void rejectsDatesOutsideTheWeekAndTakenWindows() {
        when(subscriptions.isReadyForEvaluation("draft")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> commands.handle(command(TOMORROW.minusDays(1), 10, 7L)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(command(TOMORROW.plusDays(7), 10, 7L)));
        assertThrows(AppointmentSlotTaken.class, () -> commands.handle(command(TOMORROW, 14, 7L)));
        assertThrows(AppointmentSlotTaken.class, () -> commands.handle(command(TOMORROW, 10, 8L)));

        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThrows(AppointmentSlotTaken.class, () -> commands.handle(command(TOMORROW, 10, 7L)));
    }

    private static ScheduleAppointmentCommand command(LocalDate date, int hour, Long technicianId) {
        return new ScheduleAppointmentCommand("draft", date, LocalTime.of(hour, 0), LocalTime.of(hour + 2, 0),
                technicianId, "María Villanueva", "984123456", "Puerta verde");
    }
}
