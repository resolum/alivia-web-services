package com.resolum.alivia.operations;

import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.operations.application.acl.OperationsContextFacadeImpl;
import com.resolum.alivia.operations.application.internal.commandservices.AppointmentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotFound;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.RecordEvaluationResultCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.EvaluationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalOrdersController;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The technician decides at the home whether the request moves from evaluation to confirmation. */
class EvaluationResultTest {
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    private static Appointment order(ServiceType type, int startHour, int endHour) {
        return new Appointment("draft", type, null, DAY, new TimeRange(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0)),
                7L, "Carla Mendoza", "María", "984123456", " Av. Del Parque Norte 742 ", NOW);
    }

    @Test
    void onlyAScheduledEvaluationTakesTheTechniciansDecision() {
        var evaluation = order(ServiceType.EVALUATION, 10, 12);
        assertThrows(IllegalArgumentException.class, () -> evaluation.recordEvaluationResult(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> evaluation.recordEvaluationResult(EvaluationResult.VIABLE_WITH_ADAPTATION, " "));
        assertThrows(IllegalArgumentException.class,
                () -> evaluation.recordEvaluationResult(EvaluationResult.VIABLE, "x".repeat(301)));
        assertThrows(IllegalArgumentException.class, () -> order(ServiceType.INSTALLATION, 8, 12)
                .recordEvaluationResult(EvaluationResult.VIABLE, null));

        evaluation.recordEvaluationResult(EvaluationResult.VIABLE_WITH_ADAPTATION, " 1 sensor adicional ");

        assertEquals(AppointmentStatus.COMPLETED, evaluation.getStatus());
        assertEquals("1 sensor adicional", evaluation.getCompletionNotes());
        assertTrue(evaluation.getEvaluationResult().isViable());
        assertFalse(EvaluationResult.NOT_VIABLE.isViable());
        assertThrows(TechnicalOrderNotScheduled.class,
                () -> evaluation.recordEvaluationResult(EvaluationResult.NOT_VIABLE, null));
    }

    @Test
    void recordsTheResultThroughTheTechnicalOrders() throws Exception {
        var appointments = mock(AppointmentRepository.class);
        var commands = new AppointmentCommandServiceImpl(appointments, mock(EvaluationSlotHoldRepository.class), mock(IncidentPersistenceRepository.class),
                mock(ExternalHcmService.class), mock(ExternalSubscriptionsService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        var evaluation = order(ServiceType.EVALUATION, 10, 12);
        when(appointments.findById(4L)).thenReturn(Optional.of(evaluation));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(EvaluationResult.VIABLE,
                commands.handle(new RecordEvaluationResultCommand(4L, EvaluationResult.VIABLE, null)).getEvaluationResult());
        assertThrows(TechnicalOrderNotFound.class,
                () -> commands.handle(new RecordEvaluationResultCommand(5L, EvaluationResult.VIABLE, null)));

        var service = mock(AppointmentCommandService.class);
        var closed = order(ServiceType.EVALUATION, 10, 12);
        closed.recordEvaluationResult(EvaluationResult.VIABLE_WITH_ADAPTATION, "1 sensor adicional");
        when(service.handle(new RecordEvaluationResultCommand(4L, EvaluationResult.VIABLE_WITH_ADAPTATION,
                "1 sensor adicional"))).thenReturn(closed);
        var mvc = MockMvcBuilders.standaloneSetup(new TechnicalOrdersController(service, mock(AppointmentQueryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/technical-orders/4/evaluation-result").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"VIABLE_WITH_ADAPTATION","notes":"1 sensor adicional"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationResult").value("VIABLE_WITH_ADAPTATION"))
                .andExpect(jsonPath("$.completionNotes").value("1 sensor adicional"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post("/api/v1/technical-orders/4/evaluation-result").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theEvaluationIsBookedWithoutNamingWhoReceivesTheTechnician() {
        var withoutContact = new Appointment("draft", DAY, new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0)), 7L,
                "Carla Mendoza", " ", null, "Intercomunicador 401");
        assertNull(withoutContact.getContactName());
        assertNull(withoutContact.getContactPhone());
        assertThrows(IllegalArgumentException.class, () -> new Appointment("draft", DAY,
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0)), 7L, "Carla Mendoza", "María", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("draft", DAY,
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0)), 7L, "Carla Mendoza", "x".repeat(101), "984123456", null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("draft", DAY,
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0)), 7L, "Carla Mendoza", "María", "12", null));
    }

    @Test
    void sharesTheLatestContractingVisitsOfARequest() {
        var appointments = mock(AppointmentRepository.class);
        var evaluation = order(ServiceType.EVALUATION, 10, 12);
        evaluation.recordEvaluationResult(EvaluationResult.VIABLE, null);
        var oldInstallation = order(ServiceType.INSTALLATION, 8, 12);
        var installation = order(ServiceType.INSTALLATION, 14, 18);
        installation.recordInstallationResult(InstallationResult.APPROVED, null);
        ReflectionTestUtils.setField(evaluation, "id", 1L);
        ReflectionTestUtils.setField(oldInstallation, "id", 2L);
        ReflectionTestUtils.setField(installation, "id", 3L);
        when(appointments.findByDraftIdInAndStatusIn(List.of("draft"),
                List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED)))
                .thenReturn(List.of(installation, oldInstallation, evaluation));
        var facade = new OperationsContextFacadeImpl(appointments);

        var visits = facade.fetchContractingVisitsOfDraft("draft");

        assertEquals(2, visits.size());
        assertEquals("EVALUATION", visits.getFirst().serviceType());
        assertEquals("VIABLE", visits.getFirst().result());
        assertEquals("COMPLETED", visits.getFirst().status());
        assertEquals("Av. Del Parque Norte 742", visits.getFirst().directions());
        assertEquals(LocalTime.of(14, 0), visits.get(1).startTime());
        assertEquals("APPROVED", visits.get(1).result());
        assertTrue(facade.fetchContractingVisitsOfDraft(" ").isEmpty());
        assertTrue(facade.fetchContractingVisitsOfDraft(null).isEmpty());
    }
}
