package com.resolum.alivia.subscriptions;

import com.resolum.alivia.operations.application.acl.OperationsContextFacadeImpl;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianIdByUserIdQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.application.internal.queryservices.ServicedHomeQueryServiceImpl;
import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomeByIdQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomesQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringRequest;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.services.HiringRequestQueryService;
import com.resolum.alivia.subscriptions.domain.services.ServicedHomeQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.ServicedHomesController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Homes in service: approved installations with their plan, devices and orders, for admins and the technicians who served them. */
class ServicedHomesTest {
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    private AppointmentRepository appointments;
    private AppointmentQueryService queries;
    private IncidentPersistenceRepository incidents;
    private OperationsContextFacadeImpl facade;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        queries = mock(AppointmentQueryService.class);
        incidents = mock(IncidentPersistenceRepository.class);
        facade = new OperationsContextFacadeImpl(appointments, mock(AppointmentCommandService.class), queries, incidents);

        var installed = order(1L, "draft-a", ServiceType.INSTALLATION, 7L, "Carla Mendoza", 9, 13);
        installed.registerDevice(new InstalledDevice("Sensor de ventana", "00:1A:2B:3C:4D:5E", InstalledDeviceType.SENSOR));
        installed.recordInstallationResult(InstallationResult.APPROVED, null, NOW);
        var visit = new Appointment("draft-a", ServiceType.MAINTENANCE, 4L, DAY, new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                8L, "Ana Torres", null, null, null, NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(visit, "id", 2L);
        var evaluation = order(3L, "draft-a", ServiceType.EVALUATION, 7L, "Carla Mendoza", 8, 10);
        var pending = order(4L, "draft-b", ServiceType.INSTALLATION, 9L, "Bruno Silva", 9, 13);
        when(appointments.findAll()).thenReturn(List.of(installed, visit, evaluation, pending));
        var incident = new Incident("draft-a", "draft-a", 1L, 10L, "El sensor no responde", IncidentSeverity.HIGH, NOW);
        ReflectionTestUtils.setField(incident, "id", 4L);
        when(incidents.findAllById(any())).thenReturn(List.of(incident));
    }

    private static Appointment order(Long id, String draft, ServiceType type, Long technicianId, String technician, int from, int to) {
        var order = new Appointment(draft, type, DAY, new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0)), technicianId,
                technician, "María", "984123456", "Av. Primavera 1450, Surco, Lima", NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    void onlyApprovedInstallationsAreHomesAndTechniciansSeeTheOnesTheyServed() {
        var homes = facade.fetchInstalledHomes(null);
        assertEquals(1, homes.size());
        var home = homes.getFirst();
        assertEquals("draft-a", home.draftId());
        assertEquals("Carla Mendoza", home.installedBy());
        assertEquals("Av. Primavera 1450, Surco, Lima", home.directions());
        assertEquals("SENSOR", home.devices().getFirst().type());
        assertEquals(3, home.orders().size());
        var maintenance = home.orders().stream().filter(order -> order.serviceType().equals("MAINTENANCE")).findFirst().orElseThrow();
        assertEquals("INC-2026-0004", maintenance.incidentCode());
        assertEquals("OPEN", maintenance.incidentStatus());
        assertEquals("ORD-2026-0002", maintenance.code());

        when(queries.handle(new GetTechnicianIdByUserIdQuery(70L))).thenReturn(Optional.of(8L));
        when(queries.handle(new GetTechnicianIdByUserIdQuery(71L))).thenReturn(Optional.of(9L));
        when(queries.handle(new GetTechnicianIdByUserIdQuery(72L))).thenReturn(Optional.empty());
        assertEquals(1, facade.fetchInstalledHomes(70L).size());
        assertTrue(facade.fetchInstalledHomes(71L).isEmpty());
        assertTrue(facade.fetchInstalledHomes(72L).isEmpty());
        assertTrue(new OperationsContextFacadeImpl(appointments, mock(AppointmentCommandService.class)).fetchInstalledHomes(null).isEmpty());
    }

    @Test
    void theHomeCarriesThePlanItPaysAndAdminsOrServingTechniciansReadIt() throws Exception {
        var hiring = mock(HiringRequestQueryService.class);
        when(hiring.handle(any(GetHiringRequestsQuery.class))).thenReturn(List.of(new HiringRequest("draft-a", PlanCode.MINIMUM,
                "Plan Base", "MONTHLY", 1, 2, HiringStage.COMPLETED, PaymentStatus.SUCCEEDED, new BigDecimal("99"), "PEN",
                NOW, NOW, NOW)));
        when(queries.handle(new GetTechnicianIdByUserIdQuery(70L))).thenReturn(Optional.of(8L));
        when(queries.handle(new GetTechnicianIdByUserIdQuery(71L))).thenReturn(Optional.of(9L));
        ServicedHomeQueryService service = new ServicedHomeQueryServiceImpl(new ExternalOperationsService(facade), hiring);

        var home = service.handle(new GetServicedHomeByIdQuery("draft-a", null)).orElseThrow();
        assertEquals("Plan Base", home.request().planName());
        assertEquals(new BigDecimal("99"), home.monthlyAmount());
        assertTrue(service.handle(new GetServicedHomeByIdQuery("draft-a", 71L)).isEmpty());
        assertTrue(service.handle(new GetServicedHomesQuery(71L)).isEmpty());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ServicedHomesController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        var admin = new UsernamePasswordAuthenticationToken("1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")));
        var technician = new UsernamePasswordAuthenticationToken("70", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN")));
        var other = new UsernamePasswordAuthenticationToken("71", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN")));

        mvc.perform(get("/api/v1/serviced-homes").principal(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("HOG-DRAFTA"))
                .andExpect(jsonPath("$[0].planName").value("Plan Base"))
                .andExpect(jsonPath("$[0].paymentStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].devices[0].alias").value("Sensor de ventana"))
                .andExpect(jsonPath("$[0].orders[0].startTime").exists());
        mvc.perform(get("/api/v1/serviced-homes/mine").principal(technician)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/serviced-homes/draft-a").principal(technician)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/serviced-homes/draft-a").principal(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/serviced-homes/draft-a").principal(admin)).andExpect(jsonPath("$.installedBy").value("Carla Mendoza"));

        when(hiring.handle(any(GetHiringRequestsQuery.class))).thenReturn(List.of());
        mvc.perform(get("/api/v1/serviced-homes/draft-a").principal(admin))
                .andExpect(jsonPath("$.planName").doesNotExist())
                .andExpect(jsonPath("$.rooms").value(0));
        mvc.perform(get("/api/v1/serviced-homes/mine").principal(new UsernamePasswordAuthenticationToken("abc", null, List.of())))
                .andExpect(status().isBadRequest());
    }
}
