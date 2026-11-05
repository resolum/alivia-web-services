package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.commandservices.IncidentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.application.internal.queryservices.IncidentQueryServiceImpl;
import com.resolum.alivia.operations.domain.exceptions.IncidentAlreadyOpen;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotOpen;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.BookIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.ConfirmIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.EvaluateIncidentCommand;
import com.resolum.alivia.operations.domain.model.commands.ReportIncidentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetReportableDevicesQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianIdByUserIdQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentProblemType;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportableHome;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportedDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.IncidentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.operations.interfaces.rest.controllers.IncidentsController;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalOrdersController;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The family reports a failure from the web; it is evaluated, scheduled, attended and resolved from the app. */
class IncidentReportFlowTest {
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UsernamePasswordAuthenticationToken FAMILY = new UsernamePasswordAuthenticationToken("10", null,
            List.of(new SimpleGrantedAuthority("ROLE_CAREGIVER")));
    private static final InstalledDevice WINDOW = new InstalledDevice("Sensor de ventana", "00:1A:2B:3C:4D:5E",
            InstalledDeviceType.SENSOR);
    private static final InstalledDevice MIC = new InstalledDevice("Micrófono sala", "00:1A:2B:3C:4D:5F",
            InstalledDeviceType.MICROPHONE);
    private static final SlotId SLOT = new SlotId(LocalDate.of(2026, 11, 5), new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)));

    private IncidentPersistenceRepository incidents;
    private AppointmentRepository appointments;
    private SubscriptionsContextFacade subscriptionsFacade;
    private AppointmentCommandService orders;
    private IncidentQueryServiceImpl queries;
    private IncidentCommandServiceImpl commands;
    private Appointment installation;

    @BeforeEach
    void setUp() {
        incidents = mock(IncidentPersistenceRepository.class);
        appointments = mock(AppointmentRepository.class);
        subscriptionsFacade = mock(SubscriptionsContextFacade.class);
        orders = mock(AppointmentCommandService.class);
        var subscriptions = new ExternalSubscriptionsService(subscriptionsFacade);
        queries = new IncidentQueryServiceImpl(incidents, appointments, subscriptions);
        commands = new IncidentCommandServiceImpl(incidents, appointments, subscriptions, queries, orders, CLOCK);
        when(incidents.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 84L);
            return saved;
        });
        installation = new Appointment("draft-1", ServiceType.INSTALLATION, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), 5L, "Elena Vega", "María", "984123456",
                "Av. Del Parque Norte 742", NOW.minusSeconds(86_400));
        installation.registerDevice(WINDOW);
        installation.registerDevice(MIC);
        installation.recordInstallationResult(InstallationResult.APPROVED, null, NOW.minusSeconds(3600));
        when(subscriptionsFacade.fetchActiveDraftOfAccount(10L)).thenReturn(Optional.of("draft-1"));
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft-1", ServiceType.INSTALLATION,
                AppointmentStatus.COMPLETED)).thenReturn(Optional.of(installation));
    }

    private ReportIncidentCommand report(String mac, IncidentProblemType type) {
        return new ReportIncidentCommand(10L, mac, type, "El sensor no detecta la ventana desde anoche.",
                NOW.minusSeconds(7200), SLOT, 5L);
    }

    @Test
    void anIncidentKeepsItsReportDetailsAndMovesThroughEvaluationAndScheduling() {
        var incident = new Incident("draft-1", "draft-1", 1L, 10L, "Falla del sensor", IncidentSeverity.LOW, NOW);
        assertThrows(IllegalArgumentException.class, () -> incident.describeReport(null, null, ReportedDevice.of(WINDOW), NOW));
        assertThrows(IllegalArgumentException.class, () -> incident.describeReport(IncidentProblemType.OTHER, null, null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> incident.describeReport(IncidentProblemType.OTHER, NOW.plusSeconds(60), ReportedDevice.of(WINDOW), NOW));
        incident.describeReport(IncidentProblemType.BATTERY, NOW.minusSeconds(60), ReportedDevice.of(WINDOW), NOW);
        assertEquals("SENSOR", incident.getDevice().type());
        assertEquals("INC-2026-0000", incident.code());

        incident.startEvaluation(NOW);
        assertEquals(IncidentStatus.IN_EVALUATION, incident.getStatus());
        assertThrows(IncidentNotOpen.class, () -> incident.startEvaluation(NOW));
        incident.scheduleMaintenance(NOW);
        assertEquals(List.of(IncidentStatus.OPEN, IncidentStatus.IN_EVALUATION, IncidentStatus.SCHEDULED),
                incident.getStatusHistory().stream().map(change -> change.status()).toList());
        assertThrows(IncidentNotOpen.class, () -> incident.scheduleMaintenance(NOW));

        assertThrows(IllegalArgumentException.class, () -> new ReportedDevice(" ", "00:1A", "SENSOR"));
        assertThrows(IllegalArgumentException.class, () -> new ReportedDevice("x", " ", "SENSOR"));
        assertThrows(IllegalArgumentException.class, () -> new ReportedDevice("x", "00:1A", null));
        assertEquals(IncidentSeverity.HIGH, IncidentProblemType.PHYSICAL_DAMAGE.severity());
    }

    @Test
    void theReportStaysRegisteredAndReservesTheVisitTheFamilyChose() {
        var incident = commands.handle(report("00-1a-2b-3c-4d-5f", IncidentProblemType.NO_RESPONSE));

        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals(1, incident.getStatusHistory().size());
        assertEquals(2L, incident.getDeviceId());
        assertEquals("Micrófono sala", incident.getDevice().alias());
        assertEquals(IncidentSeverity.HIGH, incident.getSeverity());
        assertEquals("draft-1", incident.getHouseholdId());
        verify(orders).handle(new BookIncidentVisitCommand(84L, SLOT, 5L));
    }

    @Test
    void theVisitMustBeChosen() {
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new ReportIncidentCommand(10L,
                "00:1A:2B:3C:4D:5E", IncidentProblemType.BATTERY, "Batería baja otra vez.", null, null, 5L)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new ReportIncidentCommand(10L,
                "00:1A:2B:3C:4D:5E", IncidentProblemType.BATTERY, "Batería baja otra vez.", null, SLOT, null)));
        verifyNoInteractions(orders);
    }

    @Test
    void onlyAnActiveHomeReportsItsOwnInstalledDevicesOnce() {
        assertThrows(IllegalArgumentException.class, () -> commands.handle(report("AA:BB:CC:DD:EE:FF", IncidentProblemType.OTHER)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(report(null, IncidentProblemType.OTHER)));
        assertThrows(IllegalArgumentException.class, () -> commands.handle(report("00:1A:2B:3C:4D:5E", null)));
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(new ReportIncidentCommand(null, "00:1A:2B:3C:4D:5E",
                IncidentProblemType.OTHER, "Descripción suficiente", null, SLOT, 5L)));

        var existing = new Incident("draft-1", "draft-1", 1L, 10L, "Falla", IncidentSeverity.LOW, NOW);
        ReflectionTestUtils.setField(existing, "id", 70L);
        when(incidents.findFirstByHouseholdIdAndDeviceIdAndStatusIn(eq("draft-1"), eq(1L), any()))
                .thenReturn(Optional.of(existing));
        assertEquals(70L, assertThrows(IncidentAlreadyOpen.class,
                () -> commands.handle(report("00:1A:2B:3C:4D:5E", IncidentProblemType.OTHER))).getIncidentId());

        when(subscriptionsFacade.fetchActiveDraftOfAccount(10L)).thenReturn(Optional.empty());
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(report("00:1A:2B:3C:4D:5E", IncidentProblemType.OTHER)));
        assertThrows(IncidentNotAllowed.class, () -> new IncidentCommandServiceImpl(incidents, appointments,
                new ExternalSubscriptionsService(subscriptionsFacade), CLOCK).handle(report("x", IncidentProblemType.OTHER)));
        verify(incidents, never()).saveAndFlush(any());
    }

    @Test
    void theHomeListsItsInstalledDevicesOnlyOnceTheInstallationWasApproved() {
        var home = queries.handle(new GetReportableDevicesQuery(10L)).orElseThrow();
        assertEquals(List.of(WINDOW, MIC), home.devices());
        assertEquals("Av. Del Parque Norte 742", home.directions());
        assertTrue(queries.handle(new GetReportableDevicesQuery(null)).isEmpty());
        assertTrue(new IncidentQueryServiceImpl(incidents).handle(new GetReportableDevicesQuery(10L)).isEmpty());

        var rejected = new Appointment("draft-2", ServiceType.INSTALLATION, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), 5L, "Elena Vega", null, null, null, NOW);
        rejected.recordInstallationResult(InstallationResult.REJECTED, null, NOW);
        when(subscriptionsFacade.fetchActiveDraftOfAccount(11L)).thenReturn(Optional.of("draft-2"));
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft-2", ServiceType.INSTALLATION,
                AppointmentStatus.COMPLETED)).thenReturn(Optional.of(rejected));
        assertTrue(queries.handle(new GetReportableDevicesQuery(11L)).isEmpty());
    }

    @Test
    void anAccountIsActiveWithTheDraftWhosePaymentWasCaptured() {
        var drafts = mock(ConfigurationDraftRepository.class);
        var facade = new SubscriptionsContextFacadeImpl(drafts, mock(SubscriptionRepository.class),
                mock(PaymentAuthorizationGateway.class), CLOCK);
        var pending = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        var active = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        ReflectionTestUtils.setField(active, "paymentStatus", PaymentStatus.SUCCEEDED);
        when(drafts.findByOwnerUserIdOrderByUpdatedAtDesc(10L)).thenReturn(List.of(pending, active));
        assertEquals(Optional.of(active.getId()), facade.fetchActiveDraftOfAccount(10L));
        assertTrue(facade.fetchActiveDraftOfAccount(null).isEmpty());
        assertEquals(Optional.of("draft-1"), new ExternalSubscriptionsService(subscriptionsFacade).fetchActiveDraftOf(10L));
    }

    @Test
    void theCaregiverWebReportsAndListsTheDevicesOfItsHome() throws Exception {
        var incidentCommands = mock(IncidentCommandService.class);
        var incidentQueries = mock(IncidentQueryService.class);
        var reported = new Incident("draft-1", "draft-1", 2L, 10L, "El micrófono no responde.", IncidentSeverity.HIGH, NOW);
        ReflectionTestUtils.setField(reported, "id", 85L);
        reported.describeReport(IncidentProblemType.NO_RESPONSE, NOW, ReportedDevice.of(MIC), NOW);
        when(incidentCommands.handle(any(ReportIncidentCommand.class))).thenReturn(reported);
        when(incidentQueries.handle(new GetReportableDevicesQuery(10L)))
                .thenReturn(Optional.of(new ReportableHome("draft-1", "Av. Del Parque Norte 742", "María", List.of(WINDOW, MIC))));
        when(incidentQueries.handle(new GetReportableDevicesQuery(11L))).thenReturn(Optional.empty());
        var appointmentQueries = mock(AppointmentQueryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new IncidentsController(incidentCommands, incidentQueries, appointmentQueries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/support-incidents/reports").principal(FAMILY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceMac":"00:1A:2B:3C:4D:5F","problemType":"NO_RESPONSE",
                                 "description":"El micrófono no responde.","occurredAt":"2026-11-04T14:00:00Z",
                                 "visitDate":"2026-11-05","visitStartTime":"08:00","visitEndTime":"10:00","technicianId":5}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INC-2026-0085"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.problemType").value("NO_RESPONSE"))
                .andExpect(jsonPath("$.device.alias").value("Micrófono sala"));
        mvc.perform(post("/api/v1/support-incidents/reports").principal(FAMILY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceMac\":\"00:1A\",\"problemType\":\"OTHER\",\"description\":\"corta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/support-incidents/devices").principal(FAMILY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices[1].id").value(2))
                .andExpect(jsonPath("$.devices[1].type").value("MICROPHONE"))
                .andExpect(jsonPath("$.directions").value("Av. Del Parque Norte 742"));
        mvc.perform(get("/api/v1/support-incidents/devices").principal(new UsernamePasswordAuthenticationToken("11", null, List.of())))
                .andExpect(status().isNotFound());
    }

    @Test
    void theTechnicianSeesTheIncidentBehindTheOrderAndResolvesIt() throws Exception {
        var appointmentCommands = mock(AppointmentCommandService.class);
        var appointmentQueries = mock(AppointmentQueryService.class);
        var incidentQueries = mock(IncidentQueryService.class);
        var order = new Appointment("draft-1", ServiceType.MAINTENANCE, 85L, SLOT.date(), SLOT.window(), 5L, "Elena Vega",
                "María", "984123456", "Av. Del Parque Norte 742", NOW);
        ReflectionTestUtils.setField(order, "id", 9L);
        var incident = new Incident("draft-1", "draft-1", 2L, 10L, "El micrófono no responde.", IncidentSeverity.HIGH, NOW);
        ReflectionTestUtils.setField(incident, "id", 85L);
        incident.describeReport(IncidentProblemType.NO_RESPONSE, null, ReportedDevice.of(MIC), NOW);
        when(appointmentQueries.handle(new GetTechnicianIdByUserIdQuery(70L))).thenReturn(Optional.of(5L));
        when(appointmentQueries.handle(new GetTechnicalOrderByIdQuery(9L))).thenReturn(Optional.of(order));
        when(incidentQueries.handle(new GetIncidentByIdQuery(85L))).thenReturn(Optional.of(incident));
        when(appointmentCommands.handle(any(CompleteMaintenanceOrderCommand.class))).thenReturn(order);
        var technician = new UsernamePasswordAuthenticationToken("70", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TechnicalOrdersController(appointmentCommands, appointmentQueries, incidentQueries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/technical-orders/9").principal(technician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.code").value("INC-2026-0085"))
                .andExpect(jsonPath("$.incident.device.type").value("MICROPHONE"))
                .andExpect(jsonPath("$.incident.problemType").value("NO_RESPONSE"));
        mvc.perform(post("/api/v1/technical-orders/9/maintenance-result").principal(technician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosis\":\"Batería agotada\",\"workPerformed\":\"Se cambió la batería y se probó.\"}"))
                .andExpect(status().isOk());
        when(appointmentCommands.handle(any(EvaluateIncidentCommand.class))).thenReturn(order);
        when(appointmentCommands.handle(any(ConfirmIncidentVisitCommand.class))).thenReturn(order);
        mvc.perform(post("/api/v1/technical-orders/9/incident-evaluation").principal(technician)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/technical-orders/9/visit-confirmation").principal(technician)).andExpect(status().isOk());
        verify(appointmentCommands).handle(new EvaluateIncidentCommand(9L));
        verify(appointmentCommands).handle(new ConfirmIncidentVisitCommand(9L));
        verify(appointmentCommands).handle(new CompleteMaintenanceOrderCommand(9L, "Batería agotada",
                "Se cambió la batería y se probó.", com.resolum.alivia.operations.domain.model.valueobjects.TestResult.PASSED,
                "Se cambió la batería y se probó."));
        mvc.perform(post("/api/v1/technical-orders/9/maintenance-result").principal(technician)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\" \",\"workPerformed\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
