package com.resolum.alivia.operations;

import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.operations.application.internal.commandservices.AppointmentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.domain.exceptions.IncidentAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotFound;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotOpen;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotScheduled;
import com.resolum.alivia.operations.domain.exceptions.SlotUnavailable;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotFound;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.StartMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatusChange;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.operations.domain.model.valueobjects.TestResult;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotInEvaluation;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotScheduled;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.commands.BookIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.ConfirmIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.EvaluateIncidentCommand;
import com.resolum.alivia.operations.domain.model.commands.StartTechnicalOrderCommand;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The family books the visit with the technician it chose; the technician then evaluates and confirms it. */
class IncidentVisitStepsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 3);
    private static final TechnicianAvailability ANA =
            new TechnicianAvailability(7L, "Ana Pérez", List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0))));
    private static final TechnicianAvailability BRUNO =
            new TechnicianAvailability(8L, "Bruno Silva", List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0))));
    private static final SlotId SLOT = new SlotId(TOMORROW, range(8, 10));

    private AppointmentRepository appointments;
    private IncidentPersistenceRepository incidents;
    private AppointmentCommandServiceImpl commands;
    private Incident incident;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        incidents = mock(IncidentPersistenceRepository.class);
        var hcm = mock(ExternalHcmService.class);
        commands = new AppointmentCommandServiceImpl(appointments, mock(EvaluationSlotHoldRepository.class), incidents, hcm,
                mock(ExternalSubscriptionsService.class), CLOCK);
        when(hcm.fetchTechniciansWorkingOn(any())).thenReturn(List.of(ANA, BRUNO));
        when(appointments.findByScheduledDateAndStatus(any(), eq(AppointmentStatus.SCHEDULED))).thenReturn(List.of());
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment saved = invocation.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", 8L);
            return saved;
        });
        incident = new Incident("draft", "draft", 5L, 10L, "El micrófono no responde", IncidentSeverity.HIGH, Instant.now(CLOCK));
        ReflectionTestUtils.setField(incident, "id", 3L);
        when(incidents.findById(3L)).thenReturn(Optional.of(incident));
        var installation = new Appointment("draft", ServiceType.INSTALLATION, TOMORROW.minusDays(10), range(8, 12), 7L,
                "Ana Pérez", "María Villanueva", "984123456", "Puerta verde", Instant.now(CLOCK));
        installation.recordInstallationResult(InstallationResult.APPROVED, null);
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION,
                AppointmentStatus.COMPLETED)).thenReturn(Optional.of(installation));
    }

    @Test
    void theChosenTechnicianIsReservedWhileTheIncidentStaysRegistered() {
        var order = commands.handle(new BookIncidentVisitCommand(3L, SLOT, 8L));

        assertEquals(8L, order.getTechnicianId());
        assertEquals("Bruno Silva", order.getTechnicianName());
        assertEquals(ServiceType.MAINTENANCE, order.getServiceType());
        assertEquals(3L, order.getIncidentId());
        assertEquals("Puerta verde", order.getDirections());
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
    }

    @Test
    void aTechnicianWhoIsNotFreeOrAnInvalidBlockIsRejected() {
        var busy = new Appointment("other", ServiceType.EVALUATION, TOMORROW, range(8, 10), 8L, "Bruno Silva",
                "Luis", "984123456", null, Instant.now(CLOCK));
        when(appointments.findByScheduledDateAndStatus(TOMORROW, AppointmentStatus.SCHEDULED)).thenReturn(List.of(busy));

        assertThrows(SlotUnavailable.class, () -> commands.handle(new BookIncidentVisitCommand(3L, SLOT, 8L)));
        assertThrows(SlotUnavailable.class, () -> commands.handle(new BookIncidentVisitCommand(3L, SLOT, 99L)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new BookIncidentVisitCommand(3L, null, 7L)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new BookIncidentVisitCommand(3L, SLOT, null)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(
                new BookIncidentVisitCommand(3L, new SlotId(TOMORROW, range(8, 12)), 7L)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(
                new BookIncidentVisitCommand(3L, new SlotId(TOMORROW.minusDays(1), range(8, 10)), 7L)));
        assertThrows(IncidentNotFound.class, () -> commands.handle(new BookIncidentVisitCommand(4L, SLOT, 7L)));

        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        assertThrows(SlotUnavailable.class, () -> commands.handle(new BookIncidentVisitCommand(3L, SLOT, 7L)));
    }

    @Test
    void theTechnicianEvaluatesConfirmsAndThenStartsTheVisit() {
        var order = commands.handle(new BookIncidentVisitCommand(3L, SLOT, 7L));
        when(appointments.findById(8L)).thenReturn(Optional.of(order));

        assertThrows(IncidentNotInEvaluation.class, () -> commands.handle(new ConfirmIncidentVisitCommand(8L)));
        assertThrows(IncidentNotScheduled.class, () -> commands.handle(new StartTechnicalOrderCommand(8L)));

        commands.handle(new EvaluateIncidentCommand(8L));
        assertEquals(IncidentStatus.IN_EVALUATION, incident.getStatus());
        assertThrows(IncidentNotOpen.class, () -> commands.handle(new EvaluateIncidentCommand(8L)));

        commands.handle(new ConfirmIncidentVisitCommand(8L));
        assertEquals(IncidentStatus.SCHEDULED, incident.getStatus());
        assertEquals(List.of(IncidentStatus.OPEN, IncidentStatus.IN_EVALUATION, IncidentStatus.SCHEDULED),
                incident.getStatusHistory().stream().map(change -> change.status()).toList());
        verify(incidents, times(2)).save(incident);

        commands.handle(new StartTechnicalOrderCommand(8L));
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertThrows(IncidentNotOpen.class, () -> commands.handle(new EvaluateIncidentCommand(8L)));
        order.completeMaintenance(new com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport("Pila", "Cambio", TestResult.PASSED, "Cambio"));
        assertThrows(TechnicalOrderNotScheduled.class, () -> commands.handle(new ConfirmIncidentVisitCommand(8L)));
    }

    @Test
    void onlyAMaintenanceOrderOfAnIncidentTakesTheseSteps() {
        var installation = new Appointment("draft", ServiceType.INSTALLATION, TOMORROW, range(8, 12), 7L, "Ana Pérez",
                "María", "984123456", null, Instant.now(CLOCK));
        when(appointments.findById(5L)).thenReturn(Optional.of(installation));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new EvaluateIncidentCommand(5L)));
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new ConfirmIncidentVisitCommand(6L)));
    }

    private static TimeRange range(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
