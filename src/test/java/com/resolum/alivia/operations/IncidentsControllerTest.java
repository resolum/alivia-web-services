package com.resolum.alivia.operations;

import com.resolum.alivia.operations.domain.exceptions.IncidentAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.IncidentAlreadyOpen;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotScheduled;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.StartMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetMaintenanceOrdersByIncidentIdsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.Evidence;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.IncidentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.interfaces.rest.controllers.IncidentsController;
import com.resolum.alivia.operations.interfaces.rest.controllers.MaintenanceOrdersController;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalOrdersController;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentsControllerTest {
    private static final String BODY = """
            {"householdId":"h-1","deviceId":5,"description":"El micrófono no responde","severity":"HIGH"}""";
    private static final UsernamePasswordAuthenticationToken FAMILY = new UsernamePasswordAuthenticationToken("10", null,
            List.of(new SimpleGrantedAuthority("ROLE_CAREGIVER")));

    private static final UsernamePasswordAuthenticationToken ADMIN = new UsernamePasswordAuthenticationToken("1", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")));
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    private IncidentCommandService commands;
    private IncidentQueryService queries;
    private AppointmentCommandService orders;
    private AppointmentQueryService appointmentQueries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(IncidentCommandService.class);
        queries = mock(IncidentQueryService.class);
        orders = mock(AppointmentCommandService.class);
        appointmentQueries = mock(AppointmentQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new IncidentsController(commands, queries, appointmentQueries), new MaintenanceOrdersController(orders),
                        new TechnicalOrdersController(orders, appointmentQueries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createsOpenIncidentsAndReportsDuplicatesAndIneligibleAccounts() throws Exception {
        var incident = new Incident("h-1", "draft", 5L, 10L, "El micrófono no responde", IncidentSeverity.HIGH, NOW);
        ReflectionTestUtils.setField(incident, "id", 3L);
        when(commands.handle(any(CreateIncidentCommand.class)))
                .thenReturn(incident).thenThrow(new IncidentAlreadyOpen(3L)).thenThrow(new IncidentNotAllowed());

        mvc.perform(post("/api/v1/support-incidents").principal(FAMILY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.draftId").doesNotExist());
        mvc.perform(post("/api/v1/support-incidents").principal(FAMILY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.incidentId").value(3));
        mvc.perform(post("/api/v1/support-incidents").principal(FAMILY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(commands, times(3)).handle(new CreateIncidentCommand(10L, "h-1", 5L, "El micrófono no responde",
                IncidentSeverity.HIGH));
    }

    @Test
    void hidesIncidentsOfOtherAccounts() throws Exception {
        var incident = new Incident("h-1", "draft", 5L, 11L, "Falla", IncidentSeverity.LOW, NOW);
        when(queries.handle(new GetIncidentByIdQuery(3L))).thenReturn(Optional.of(incident));

        mvc.perform(get("/api/v1/support-incidents/3").principal(FAMILY)).andExpect(status().isNotFound());
    }

    @Test
    void showsTheTimelineAndTheVisitsOfTheIncident() throws Exception {
        var incident = new Incident("h-1", "draft", 5L, 10L, "Falla", IncidentSeverity.LOW, NOW);
        ReflectionTestUtils.setField(incident, "id", 3L);
        incident.scheduleMaintenance(NOW.plusSeconds(3600));
        incident.startAttention(NOW.plusSeconds(86400));
        var visit = new Appointment("draft", ServiceType.MAINTENANCE, 3L, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)), 7L, "Ana Pérez", "María", "984123456", null, NOW);
        when(queries.handle(new GetIncidentByIdQuery(3L))).thenReturn(Optional.of(incident));
        when(appointmentQueries.handle(new GetMaintenanceOrdersByIncidentIdsQuery(List.of(3L)))).thenReturn(List.of(visit));

        mvc.perform(get("/api/v1/support-incidents/3").principal(FAMILY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.statusHistory.length()").value(3))
                .andExpect(jsonPath("$.statusHistory[1].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.statusHistory[2].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.visits[0].technicianName").value("Ana Pérez"))
                .andExpect(jsonPath("$.visits[0].scheduledDate").value("2026-11-03"));
    }

    @Test
    void listsOwnIncidentsAndAllOfThemForTheAdministrator() throws Exception {
        var incident = new Incident("h-1", "draft", 5L, 10L, "Falla", IncidentSeverity.LOW, NOW);
        when(queries.handle(new GetIncidentsQuery(10L, IncidentStatus.OPEN))).thenReturn(List.of(incident));
        when(queries.handle(new GetIncidentsQuery(null, null))).thenReturn(List.of());

        mvc.perform(get("/api/v1/support-incidents").param("status", "OPEN").principal(FAMILY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statusHistory[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].visits").isEmpty());
        mvc.perform(get("/api/v1/support-incidents").principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/support-incidents").principal(
                        new UsernamePasswordAuthenticationToken("ana", null, List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachesEvidenceOfTheReporter() throws Exception {
        var incident = new Incident("h-1", "draft", 5L, 10L, "Falla", IncidentSeverity.LOW, NOW);
        incident.attachEvidence(new Evidence("https://res.cloudinary.com/a.jpg", "a", NOW));
        when(commands.handle(new AttachEvidenceCommand(3L, 10L, "https://res.cloudinary.com/a.jpg", "a")))
                .thenReturn(incident);
        when(commands.handle(new AttachEvidenceCommand(4L, 10L, "https://res.cloudinary.com/a.jpg", "a")))
                .thenThrow(new IncidentAccessDenied());

        mvc.perform(post("/api/v1/support-incidents/3/evidences").principal(FAMILY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://res.cloudinary.com/a.jpg","publicId":"a"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidences[0].publicId").value("a"));
        mvc.perform(post("/api/v1/support-incidents/4/evidences").principal(FAMILY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://res.cloudinary.com/a.jpg","publicId":"a"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void schedulesStartsAndClosesTheVisitsOfAnIncident() throws Exception {
        var visit = new Appointment("draft", ServiceType.MAINTENANCE, 3L, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)), 7L, "Ana Pérez", "María", "984123456", null, NOW);
        var installation = new Appointment("draft", ServiceType.INSTALLATION, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), 7L, "Ana Pérez", "María", "984123456", null, NOW);
        installation.recordInstallationResult(InstallationResult.APPROVED, "Listo");
        when(orders.handle(any(CreateMaintenanceOrderCommand.class))).thenReturn(visit);
        when(orders.handle(new StartMaintenanceOrderCommand(8L))).thenReturn(visit);
        when(orders.handle(new StartMaintenanceOrderCommand(9L))).thenThrow(new IncidentNotScheduled());
        when(orders.handle(any(RecordInstallationResultCommand.class))).thenReturn(installation);

        mvc.perform(post("/api/v1/maintenance-orders").principal(FAMILY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incidentId":3,"slotId":"2026-11-03T08:00-10:00"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(3));
        verify(orders).handle(new CreateMaintenanceOrderCommand(3L, SlotId.parse("2026-11-03T08:00-10:00"), 10L, false));
        mvc.perform(post("/api/v1/maintenance-orders/8/start")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/maintenance-orders/9/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("incident_not_scheduled"));
        mvc.perform(post("/api/v1/technical-orders/5/installation-result").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","notes":"Listo"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationResult").value("APPROVED"));
        verify(orders).handle(new RecordInstallationResultCommand(5L, InstallationResult.APPROVED, "Listo"));
    }

    @Test
    void refusesToCloseAMaintenanceOrderWithoutTestResult() throws Exception {
        when(orders.handle(any(CompleteMaintenanceOrderCommand.class)))
                .thenThrow(new IllegalArgumentException("The order cannot close without the test result."));

        mvc.perform(post("/api/v1/maintenance-orders/8/completion").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagnosis":"Cable suelto","maintenancePerformed":"Se reconectó"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The order cannot close without the test result."));
    }
}
