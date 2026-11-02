package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.commandservices.AppointmentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.domain.exceptions.DraftNotReadyForAppointment;
import com.resolum.alivia.operations.domain.exceptions.EvaluationRequiredForInstallation;
import com.resolum.alivia.operations.domain.exceptions.SlotUnavailable;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderAlreadyExists;
import com.resolum.alivia.operations.domain.exceptions.TechnicianNotEligible;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.AssignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalAvailabilityQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.AssignmentReason;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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

class TechnicalOrderServicesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 29);
    private static final LocalDate NEXT_DAY = TOMORROW.plusDays(1);
    private static final TechnicianAvailability ANA = technician(7L, "Ana Pérez");
    private static final TechnicianAvailability LUIS = technician(8L, "Luis Rojas");

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
        when(hcm.fetchTechniciansWorkingOn(TOMORROW)).thenReturn(List.of(ANA, LUIS));
        when(hcm.fetchTechniciansWorkingOn(NEXT_DAY)).thenReturn(List.of(ANA, LUIS));
        when(appointments.findByScheduledDateAndStatus(any(), eq(AppointmentStatus.SCHEDULED))).thenReturn(List.of());
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptions.isReadyForEvaluation("draft")).thenReturn(true);
    }

    @Test
    void listsBlocksWithCapacityPerServiceType() {
        var evaluation = queries.handle(new GetTechnicalAvailabilityQuery(ServiceType.EVALUATION, TOMORROW, 1));
        var installation = queries.handle(new GetTechnicalAvailabilityQuery(ServiceType.INSTALLATION, TOMORROW, 1));

        assertEquals(List.of("2026-10-29T08:00-10:00", "2026-10-29T10:00-12:00"),
                evaluation.stream().map(block -> block.slotId().value()).toList());
        assertEquals(2, evaluation.getFirst().capacity());
        assertEquals(1, installation.size());
        assertEquals("2026-10-29T08:00-12:00", installation.getFirst().slotId().value());
    }

    @Test
    void createsAnEvaluationOrderReservingATechnicianOfTheBlock() {
        var order = commands.handle(evaluation(TOMORROW, 8));

        assertEquals(ServiceType.EVALUATION, order.getServiceType());
        assertEquals(7L, order.getTechnicianId());
        assertEquals(AppointmentStatus.SCHEDULED, order.getStatus());
        assertEquals(AssignmentReason.BOOKED, order.getAssignments().getFirst().reason());
    }

    @Test
    void answersWithAlternativesWhenTheBlockRanOut() {
        when(appointments.findByScheduledDateAndStatus(TOMORROW, AppointmentStatus.SCHEDULED))
                .thenReturn(List.of(visit(1L, ANA, TOMORROW, 8), visit(2L, LUIS, TOMORROW, 8)));

        var conflict = assertThrows(SlotUnavailable.class, () -> commands.handle(evaluation(TOMORROW, 8)));

        assertEquals(List.of("2026-10-29T10:00-12:00", "2026-10-30T08:00-10:00", "2026-10-30T10:00-12:00"),
                conflict.getAlternatives().stream().map(block -> block.slotId().value()).toList());
        verify(appointments, never()).saveAndFlush(any());

        reset(appointments);
        when(appointments.findByScheduledDateAndStatus(any(), eq(AppointmentStatus.SCHEDULED))).thenReturn(List.of());
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThrows(SlotUnavailable.class, () -> commands.handle(evaluation(TOMORROW, 8)));
    }

    @Test
    void validatesTheEvaluationRequest() {
        when(subscriptions.isReadyForEvaluation("draft")).thenReturn(false);
        assertThrows(DraftNotReadyForAppointment.class, () -> commands.handle(evaluation(TOMORROW, 8)));
        when(subscriptions.isReadyForEvaluation("draft")).thenReturn(true);
        when(appointments.existsByDraftIdAndServiceTypeAndStatus("draft", ServiceType.EVALUATION, AppointmentStatus.SCHEDULED))
                .thenReturn(true);
        assertThrows(TechnicalOrderAlreadyExists.class, () -> commands.handle(evaluation(TOMORROW, 8)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new CreateTechnicalOrderCommand("draft",
                ServiceType.EVALUATION, new SlotId(TOMORROW, range(8, 12)), "María", "984123456", null)));
        when(appointments.existsByDraftIdAndServiceTypeAndStatus(any(), any(), any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> commands.handle(evaluation(TOMORROW.plusDays(7), 8)));
    }

    @Test
    void schedulesTheInstallationAfterTheEvaluationWithItsContact() {
        assertThrows(EvaluationRequiredForInstallation.class, () -> commands.handle(installation(NEXT_DAY)));
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.EVALUATION, AppointmentStatus.SCHEDULED))
                .thenReturn(Optional.of(visit(1L, ANA, TOMORROW, 8)));
        assertThrows(EvaluationRequiredForInstallation.class, () -> commands.handle(installation(TOMORROW)));

        var order = commands.handle(installation(NEXT_DAY));

        assertEquals(ServiceType.INSTALLATION, order.getServiceType());
        assertEquals("María Villanueva", order.getContactName());
        assertEquals("Puerta verde", order.getDirections());
        assertEquals(LocalTime.of(12, 0), order.getEndTime());
    }

    @Test
    void assignsOnlyEligibleTechniciansKeepingTheOrderScheduled() {
        var order = visit(1L, ANA, TOMORROW, 8);
        when(appointments.findById(1L)).thenReturn(Optional.of(order));
        when(appointments.findByScheduledDateAndStatus(TOMORROW, AppointmentStatus.SCHEDULED)).thenReturn(List.of(order));

        assertThrows(TechnicianNotEligible.class, () -> commands.handle(new AssignTechnicianCommand(1L, 99L, null)));
        var assigned = commands.handle(new AssignTechnicianCommand(1L, 8L, "Vive cerca"));

        assertEquals(8L, assigned.getTechnicianId());
        assertEquals(AppointmentStatus.SCHEDULED, assigned.getStatus());
        assertEquals(AssignmentReason.ASSIGNED, assigned.getAssignments().getLast().reason());
    }

    @Test
    void reassignsInTheSameBlockOrMovesToTheNearestOne() {
        var order = visit(1L, ANA, TOMORROW, 8);
        when(appointments.findById(1L)).thenReturn(Optional.of(order));
        when(appointments.findByScheduledDateAndStatus(TOMORROW, AppointmentStatus.SCHEDULED)).thenReturn(List.of(order));

        commands.handle(new ReassignTechnicianCommand(1L, "Ana tiene descanso médico"));
        assertEquals(8L, order.getTechnicianId());
        assertEquals("2026-10-29T08:00-10:00", order.slotId().value());

        // Now Luis is the one who falls ill and Ana is busy at 08:00 with another home: the order moves.
        var other = visit(2L, ANA, TOMORROW, 8);
        when(appointments.findByScheduledDateAndStatus(TOMORROW, AppointmentStatus.SCHEDULED)).thenReturn(List.of(order, other));
        commands.handle(new ReassignTechnicianCommand(1L, "Luis tuvo una emergencia"));

        assertEquals(7L, order.getTechnicianId());
        assertEquals("2026-10-29T10:00-12:00", order.slotId().value());
        assertEquals(3, order.getAssignments().size());
        assertEquals("Luis tuvo una emergencia", order.getAssignments().getLast().note());
    }

    @Test
    void reportsAConflictWhenNobodyCanTakeTheOrder() {
        var order = visit(1L, ANA, TOMORROW, 8);
        when(appointments.findById(1L)).thenReturn(Optional.of(order));
        when(hcm.fetchTechniciansWorkingOn(any())).thenReturn(List.of(ANA));

        assertThrows(SlotUnavailable.class, () -> commands.handle(new ReassignTechnicianCommand(1L, "Ana renunció")));
        assertEquals(7L, order.getTechnicianId());
    }

    private static CreateTechnicalOrderCommand evaluation(LocalDate date, int hour) {
        return new CreateTechnicalOrderCommand("draft", ServiceType.EVALUATION, new SlotId(date, range(hour, hour + 2)),
                "María Villanueva", "984123456", "Puerta verde");
    }

    private static CreateTechnicalOrderCommand installation(LocalDate date) {
        return new CreateTechnicalOrderCommand("draft", ServiceType.INSTALLATION, new SlotId(date, range(8, 12)),
                null, null, null);
    }

    private static Appointment visit(Long id, TechnicianAvailability technician, LocalDate date, int hour) {
        var visit = new Appointment("draft-" + id, ServiceType.EVALUATION, date, range(hour, hour + 2),
                technician.technicianId(), technician.fullName(), "María Villanueva", "984123456", "Puerta verde",
                Instant.now(CLOCK));
        ReflectionTestUtils.setField(visit, "id", id);
        return visit;
    }

    private static TechnicianAvailability technician(Long id, String name) {
        return new TechnicianAvailability(id, name, List.of(range(8, 12)));
    }

    private static TimeRange range(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
