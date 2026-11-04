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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MaintenanceOrderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 3);
    private static final TechnicianAvailability ANA =
            new TechnicianAvailability(7L, "Ana Pérez", List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0))));
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
        var subscriptions = mock(ExternalSubscriptionsService.class);
        when(subscriptions.capturePaymentOf(anyString())).thenReturn(true);
        commands = new AppointmentCommandServiceImpl(appointments, mock(EvaluationSlotHoldRepository.class), incidents, hcm, subscriptions, CLOCK);
        when(hcm.fetchTechniciansWorkingOn(any())).thenReturn(List.of(ANA));
        when(appointments.findByScheduledDateAndStatus(any(), eq(AppointmentStatus.SCHEDULED))).thenReturn(List.of());
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        incident = new Incident("h-1", "draft", 5L, 10L, "El micrófono no responde", IncidentSeverity.HIGH, Instant.now(CLOCK));
        ReflectionTestUtils.setField(incident, "id", 3L);
        when(incidents.findById(3L)).thenReturn(Optional.of(incident));
        var installation = new Appointment("draft", ServiceType.INSTALLATION, TOMORROW.minusDays(10), range(8, 12), 7L,
                "Ana Pérez", "María Villanueva", "984123456", "Puerta verde", Instant.now(CLOCK));
        installation.recordInstallationResult(InstallationResult.APPROVED, null);
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION,
                AppointmentStatus.COMPLETED)).thenReturn(Optional.of(installation));
    }

    @Test
    void schedulesTheVisitReservingTechnicalCapacity() {
        var order = commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 10L, false));

        assertEquals(ServiceType.MAINTENANCE, order.getServiceType());
        assertEquals(AppointmentStatus.SCHEDULED, order.getStatus());
        assertEquals(3L, order.getIncidentId());
        assertEquals(7L, order.getTechnicianId());
        assertEquals("María Villanueva", order.getContactName());
        assertEquals(IncidentStatus.SCHEDULED, incident.getStatus());
        verify(incidents).save(incident);
        assertThrows(IncidentNotOpen.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 10L, false)));
    }

    @Test
    void onlyTheReporterOrAnAdministratorBooksIt() {
        assertThrows(IncidentAccessDenied.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 11L, false)));
        assertEquals(ServiceType.MAINTENANCE, commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 99L, true)).getServiceType());
    }

    @Test
    void answersWithAlternativesWhenTheBlockIsFull() {
        var busy = new Appointment("other", ServiceType.EVALUATION, TOMORROW, range(8, 10), 7L, "Ana Pérez",
                "Luis", "984123456", null, Instant.now(CLOCK));
        when(appointments.findByScheduledDateAndStatus(TOMORROW, AppointmentStatus.SCHEDULED)).thenReturn(List.of(busy));

        var conflict = assertThrows(SlotUnavailable.class,
                () -> commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 10L, false)));

        assertEquals("2026-11-03T10:00-12:00", conflict.getAlternatives().getFirst().slotId().value());
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L,
                new SlotId(TOMORROW, range(8, 12)), 10L, false)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new CreateTechnicalOrderCommand("draft",
                ServiceType.MAINTENANCE, SLOT, "María", "984123456", null)));
    }

    @Test
    void resolvesTheIncidentWhenTheTestPassesAndReopensItWhenItFails() {
        var order = commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 10L, false));
        ReflectionTestUtils.setField(order, "id", 8L);
        when(appointments.findById(8L)).thenReturn(Optional.of(order));

        var missingTest = assertThrows(IllegalArgumentException.class, () -> commands.handle(
                new CompleteMaintenanceOrderCommand(8L, "Micrófono desconectado", "Se reconectó", null, null)));
        assertTrue(missingTest.getMessage().contains("test result"));
        assertEquals(AppointmentStatus.SCHEDULED, order.getStatus());
        assertEquals(IncidentStatus.SCHEDULED, incident.getStatus());

        commands.handle(new CompleteMaintenanceOrderCommand(8L, "Micrófono desconectado", "Se reconectó el cable",
                TestResult.FAILED, null));
        assertEquals(AppointmentStatus.COMPLETED, order.getStatus());
        assertEquals(IncidentStatus.OPEN, incident.getStatus());

        var second = commands.handle(new CreateMaintenanceOrderCommand(3L, new SlotId(TOMORROW, range(10, 12)), 10L, false));
        ReflectionTestUtils.setField(second, "id", 9L);
        when(appointments.findById(9L)).thenReturn(Optional.of(second));
        commands.handle(new CompleteMaintenanceOrderCommand(9L, "Placa dañada", "Se cambió el micrófono",
                TestResult.PASSED, " Micrófono reemplazado y probado "));

        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertEquals("Micrófono reemplazado y probado", incident.getResolution());
        assertEquals(TestResult.PASSED, second.getMaintenanceReport().testResult());
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceReport("d", "m", TestResult.PASSED, " "));
    }

    @Test
    void startsTheAttentionOfAScheduledVisitAndKeepsTheTimeline() {
        var order = commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 10L, false));
        ReflectionTestUtils.setField(order, "id", 8L);
        when(appointments.findById(8L)).thenReturn(Optional.of(order));

        commands.handle(new StartMaintenanceOrderCommand(8L));
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertEquals(AppointmentStatus.SCHEDULED, order.getStatus());
        assertThrows(IncidentNotScheduled.class, () -> commands.handle(new StartMaintenanceOrderCommand(8L)));

        commands.handle(new CompleteMaintenanceOrderCommand(8L, "Cable suelto", "Se reconectó", TestResult.PASSED,
                "Micrófono funcionando"));
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertEquals(List.of(IncidentStatus.OPEN, IncidentStatus.SCHEDULED, IncidentStatus.IN_PROGRESS,
                        IncidentStatus.RESOLVED),
                incident.getStatusHistory().stream().map(IncidentStatusChange::status).toList());
        assertTrue(incident.getStatusHistory().stream().allMatch(change -> change.changedAt().equals(Instant.now(CLOCK))));
        assertThrows(TechnicalOrderNotScheduled.class, () -> commands.handle(new StartMaintenanceOrderCommand(8L)));
    }

    @Test
    void rejectsVisitsOutsideTheMaintenanceRulesAndHomesWithoutInstallation() {
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L, null, 10L, false)));
        assertThrows(IncidentNotFound.class, () -> commands.handle(new CreateMaintenanceOrderCommand(4L, SLOT, 10L, false)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L,
                new SlotId(TOMORROW.minusDays(1), range(8, 10)), 10L, false)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L,
                new SlotId(TOMORROW.plusDays(30), range(8, 10)), 10L, false)));
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION,
                AppointmentStatus.COMPLETED)).thenReturn(Optional.empty());
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(new CreateMaintenanceOrderCommand(3L, SLOT, 10L, false)));
    }

    @Test
    void startsOnlyMaintenanceOrdersThatExist() {
        var installation = new Appointment("draft", ServiceType.INSTALLATION, TOMORROW, range(8, 12), 7L, "Ana Pérez",
                "María", "984123456", null, Instant.now(CLOCK));
        when(appointments.findById(5L)).thenReturn(Optional.of(installation));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new StartMaintenanceOrderCommand(5L)));
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new StartMaintenanceOrderCommand(6L)));
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new CompleteMaintenanceOrderCommand(6L, "d", "m",
                TestResult.FAILED, null)));
    }

    @Test
    void recordsTheResultOfAnInstallationOrder() {
        var installation = new Appointment("draft", ServiceType.INSTALLATION, TOMORROW, range(8, 12), 7L, "Ana Pérez",
                "María", "984123456", null, Instant.now(CLOCK));
        when(appointments.findById(5L)).thenReturn(Optional.of(installation));

        var order = commands.handle(new RecordInstallationResultCommand(5L, InstallationResult.APPROVED, " Todo conectado "));

        assertEquals(AppointmentStatus.COMPLETED, order.getStatus());
        assertEquals("Todo conectado", order.getCompletionNotes());
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new RecordInstallationResultCommand(6L,
                InstallationResult.APPROVED, null)));
    }

    @Test
    void onlyMaintenanceOrdersAttendIncidents() {
        assertThrows(IllegalArgumentException.class, () -> new Appointment("draft", ServiceType.MAINTENANCE, null,
                TOMORROW, range(8, 10), 7L, "Ana", "María", "984123456", null, Instant.now(CLOCK)));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("draft", ServiceType.EVALUATION, 3L,
                TOMORROW, range(8, 10), 7L, "Ana", "María", "984123456", null, Instant.now(CLOCK)));
    }

    private static TimeRange range(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
