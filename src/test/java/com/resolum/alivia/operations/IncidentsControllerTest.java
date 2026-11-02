package com.resolum.alivia.operations;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.Instant;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.queries.GetMaintenanceOrdersByIncidentIdsQuery;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.exceptions.IncidentAlreadyOpen;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.interfaces.rest.controllers.IncidentsController;
import com.resolum.alivia.operations.interfaces.rest.controllers.MaintenanceOrdersController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        mvc = MockMvcBuilders.standaloneSetup(new IncidentsController(commands, queries, appointmentQueries), new MaintenanceOrdersController(orders))
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
        var visit = new Appointment("draft", ServiceType.MAINTENANCE, 3L, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)), 7L, "Ana Pérez", "María", "984123456", null, NOW);
        when(queries.handle(new GetIncidentByIdQuery(3L))).thenReturn(Optional.of(incident));
        when(appointmentQueries.handle(new GetMaintenanceOrdersByIncidentIdsQuery(List.of(3L)))).thenReturn(List.of(visit));

        mvc.perform(get("/api/v1/support-incidents/3").principal(FAMILY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[1].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.visits[0].technicianName").value("Ana Pérez"))
                .andExpect(jsonPath("$.visits[0].scheduledDate").value("2026-11-03"));
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
