package com.resolum.alivia.operations;

import com.resolum.alivia.hcm.application.acl.HcmContextFacadeImpl;
import com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import com.resolum.alivia.operations.application.internal.commandservices.AppointmentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.domain.exceptions.InstalledDeviceAlreadyRegistered;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotFound;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.OpenInstallationCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordEvaluationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.RegisterInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.RemoveInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.StartTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianIdByUserIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianOrdersQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.EvaluationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalOrdersController;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The technician attends the assigned orders from the mobile app: evaluation, refund and installation at the home. */
class TechnicianFieldOrdersTest {
    /** 10:07 in Lima. */
    private static final Instant NOW = Instant.parse("2026-11-03T15:07:30Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private static final UsernamePasswordAuthenticationToken TECHNICIAN = new UsernamePasswordAuthenticationToken("70",
            null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN")));
    private static final UsernamePasswordAuthenticationToken ADMIN = new UsernamePasswordAuthenticationToken("1",
            null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")));

    private static Appointment order(ServiceType type, Long id) {
        var order = new Appointment("draft", type, null, DAY, new TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                7L, "Carla Mendoza", "María", "984123456", "Av. Del Parque Norte 742", NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private static InstalledDevice microphone(String mac) {
        return new InstalledDevice(" Micrófono Cabecera ", mac, InstalledDeviceType.MICROPHONE);
    }

    @Test
    void theOrderKeepsWhenItStartedAndClosed() {
        var evaluation = order(ServiceType.EVALUATION, 1L);
        assertFalse(evaluation.isStarted());
        assertThrows(IllegalArgumentException.class, () -> evaluation.start(null));

        evaluation.start(NOW);
        evaluation.start(NOW.plusSeconds(60));
        assertTrue(evaluation.isStarted());
        assertEquals(NOW, evaluation.getStartedAt());
        assertTrue(evaluation.isAssignedTo(7L));
        assertFalse(evaluation.isAssignedTo(8L));

        evaluation.recordEvaluationResult(EvaluationResult.VIABLE, null, NOW.plusSeconds(900));
        assertEquals(NOW.plusSeconds(900), evaluation.getCompletedAt());
        assertThrows(TechnicalOrderNotScheduled.class, () -> evaluation.start(NOW));

        var installation = order(ServiceType.INSTALLATION, 2L);
        installation.recordInstallationResult(InstallationResult.REJECTED, " ", NOW);
        assertEquals(NOW, installation.getCompletedAt());
        assertNull(installation.getCompletionNotes());
    }

    @Test
    void anInstallationRegistersEachDeviceOnceByItsMacAddress() {
        var device = microphone("00-1a-2b-3c-4d-5e");
        assertEquals("00:1A:2B:3C:4D:5E", device.macAddress());
        assertEquals("Micrófono Cabecera", device.alias());
        assertThrows(IllegalArgumentException.class, () -> microphone("00:1A:2B:3C:4D"));
        assertThrows(IllegalArgumentException.class, () -> microphone(null));
        assertThrows(IllegalArgumentException.class, () -> new InstalledDevice(" ", "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB));
        assertThrows(IllegalArgumentException.class, () -> new InstalledDevice(null, "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB));
        assertThrows(IllegalArgumentException.class, () -> new InstalledDevice("x".repeat(61), "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB));
        assertThrows(IllegalArgumentException.class, () -> new InstalledDevice("Hub", "00:1A:2B:3C:4D:5E", null));

        var installation = order(ServiceType.INSTALLATION, 2L);
        installation.registerDevice(device);
        assertThrows(InstalledDeviceAlreadyRegistered.class, () -> installation.registerDevice(microphone("00:1A:2B:3C:4D:5E")));
        assertThrows(IllegalArgumentException.class, () -> installation.registerDevice(null));
        installation.registerDevice(new InstalledDevice("Sensor puerta", "AA:BB:CC:DD:EE:FF", InstalledDeviceType.SENSOR));
        assertEquals(2, installation.getInstalledDevices().size());

        installation.removeDevice("aa-bb-cc-dd-ee-ff");
        installation.removeDevice("11:22:33:44:55:66");
        assertEquals(List.of(device), installation.getInstalledDevices());

        assertThrows(IllegalArgumentException.class, () -> order(ServiceType.EVALUATION, 1L).registerDevice(device));
    }

    @Test
    void aHomeThatIsNotViableReleasesTheFamilysPayment() {
        var appointments = mock(AppointmentRepository.class);
        var subscriptions = mock(ExternalSubscriptionsService.class);
        var commands = commands(appointments, subscriptions, mock(IncidentPersistenceRepository.class));
        when(appointments.findById(1L)).thenReturn(Optional.of(order(ServiceType.EVALUATION, 1L)));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var closed = commands.handle(new RecordEvaluationResultCommand(1L, EvaluationResult.NOT_VIABLE, "Sin red eléctrica"));

        assertEquals(AppointmentStatus.COMPLETED, closed.getStatus());
        assertEquals(NOW, closed.getCompletedAt());
        verify(subscriptions).releasePaymentOf("draft");
        verify(appointments, times(1)).saveAndFlush(any(Appointment.class));
    }

    @Test
    void aViableHomeWaitsForTheFamilyToConfirm() {
        var appointments = mock(AppointmentRepository.class);
        var subscriptions = mock(ExternalSubscriptionsService.class);
        var commands = commands(appointments, subscriptions, mock(IncidentPersistenceRepository.class));
        when(appointments.findById(1L)).thenReturn(Optional.of(order(ServiceType.EVALUATION, 1L)));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var evaluated = commands.handle(new RecordEvaluationResultCommand(1L, EvaluationResult.VIABLE, null));

        assertEquals(AppointmentStatus.COMPLETED, evaluated.getStatus());
        // Only the evaluation is saved: the installation opens when the family confirms from the web.
        verify(appointments, times(1)).saveAndFlush(any(Appointment.class));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void theFamilysConfirmationOpensTheInstallationForTheSameTechnician() {
        var appointments = mock(AppointmentRepository.class);
        var commands = commands(appointments, mock(ExternalSubscriptionsService.class), mock(IncidentPersistenceRepository.class));
        var evaluation = order(ServiceType.EVALUATION, 1L);
        evaluation.recordEvaluationResult(EvaluationResult.VIABLE, null, NOW.minusSeconds(60));
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION, AppointmentStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.EVALUATION, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.of(evaluation));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Another order of the technician already starts at 10:07 today, and one from yesterday does not count.
        var sameMinute = new Appointment("other", ServiceType.EVALUATION, null, DAY,
                new TimeRange(LocalTime.of(10, 7), LocalTime.of(12, 7)), 7L, "Carla Mendoza", null, null, null, NOW);
        var yesterday = new Appointment("other", ServiceType.EVALUATION, null, DAY.minusDays(1),
                new TimeRange(LocalTime.of(10, 8), LocalTime.of(12, 8)), 7L, "Carla Mendoza", null, null, null, NOW);
        when(appointments.findByTechnicianIdOrderByScheduledDateAscStartTimeAsc(7L)).thenReturn(List.of(yesterday, sameMinute, evaluation));

        var installation = commands.handle(new OpenInstallationCommand("draft")).orElseThrow();

        assertEquals(ServiceType.INSTALLATION, installation.getServiceType());
        assertEquals(DAY, installation.getScheduledDate());
        assertEquals(LocalTime.of(10, 8), installation.getStartTime());
        assertEquals(LocalTime.of(14, 8), installation.getEndTime());
        assertEquals(7L, installation.getTechnicianId());
        assertEquals("María", installation.getContactName());
        // The technician starts it from the app, like any other order.
        assertNull(installation.getStartedAt());
    }

    @Test
    void theInstallationOpensOnceOnlyAfterAViableEvaluationAndWithinTheDay() {
        var appointments = mock(AppointmentRepository.class);
        var commands = commands(appointments, mock(ExternalSubscriptionsService.class), mock(IncidentPersistenceRepository.class));
        var open = order(ServiceType.INSTALLATION, 2L);
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION, AppointmentStatus.SCHEDULED))
                .thenReturn(Optional.of(open));
        assertSame(open, commands.handle(new OpenInstallationCommand("draft")).orElseThrow());
        assertTrue(commands.handle(new OpenInstallationCommand(" ")).isEmpty());
        assertTrue(commands.handle(new OpenInstallationCommand(null)).isEmpty());

        var notViable = order(ServiceType.EVALUATION, 1L);
        notViable.recordEvaluationResult(EvaluationResult.NOT_VIABLE, null, NOW);
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION, AppointmentStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.EVALUATION, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.of(notViable));
        assertTrue(commands.handle(new OpenInstallationCommand("draft")).isEmpty());
        when(appointments.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.EVALUATION, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.empty());
        assertTrue(commands.handle(new OpenInstallationCommand("draft")).isEmpty());
        verify(appointments, never()).saveAndFlush(any(Appointment.class));

        var late = mock(AppointmentRepository.class);
        var viable = order(ServiceType.EVALUATION, 1L);
        viable.recordEvaluationResult(EvaluationResult.VIABLE, null, NOW);
        when(late.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.INSTALLATION, AppointmentStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(late.findFirstByDraftIdAndServiceTypeAndStatus("draft", ServiceType.EVALUATION, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.of(viable));
        when(late.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var lateNight = new AppointmentCommandServiceImpl(late, mock(EvaluationSlotHoldRepository.class),
                mock(IncidentPersistenceRepository.class), mock(ExternalHcmService.class), mock(ExternalSubscriptionsService.class),
                Clock.fixed(Instant.parse("2026-11-04T04:59:30Z"), ZoneOffset.UTC));
        var installation = lateNight.handle(new OpenInstallationCommand("draft")).orElseThrow();
        assertEquals(LocalTime.of(23, 58), installation.getStartTime());
        assertEquals(LocalTime.of(23, 59), installation.getEndTime());
    }

    @Test
    void startsTheOrderAndRegistersItsDevices() {
        var appointments = mock(AppointmentRepository.class);
        var incidents = mock(IncidentPersistenceRepository.class);
        var subscriptions = mock(ExternalSubscriptionsService.class);
        when(subscriptions.capturePaymentOf("draft")).thenReturn(true);
        var commands = commands(appointments, subscriptions, incidents);
        var installation = order(ServiceType.INSTALLATION, 2L);
        when(appointments.findById(2L)).thenReturn(Optional.of(installation));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(NOW, commands.handle(new StartTechnicalOrderCommand(2L)).getStartedAt());
        commands.handle(new RegisterInstalledDeviceCommand(2L, "Micrófono Cabecera", "00:1a:2b:3c:4d:5e",
                InstalledDeviceType.MICROPHONE));
        assertEquals("00:1A:2B:3C:4D:5E", installation.getInstalledDevices().getFirst().macAddress());
        assertTrue(commands.handle(new RemoveInstalledDeviceCommand(2L, "00:1A:2B:3C:4D:5E")).getInstalledDevices().isEmpty());
        commands.handle(new RecordInstallationResultCommand(2L, InstallationResult.APPROVED, null));
        assertEquals(NOW, installation.getCompletedAt());
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new StartTechnicalOrderCommand(9L)));
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new RegisterInstalledDeviceCommand(9L, "a",
                "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB)));
        assertThrows(TechnicalOrderNotFound.class, () -> commands.handle(new RemoveInstalledDeviceCommand(9L, "x")));

        var maintenance = new Appointment("draft", ServiceType.MAINTENANCE, 5L, DAY,
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)), 7L, "Carla Mendoza", "María", "984123456", null, NOW);
        var incident = mock(Incident.class);
        when(appointments.findById(3L)).thenReturn(Optional.of(maintenance));
        when(incidents.findById(5L)).thenReturn(Optional.of(incident));
        assertEquals(NOW, commands.handle(new StartTechnicalOrderCommand(3L)).getStartedAt());
        verify(incident).startAttention(NOW);
    }

    @Test
    void listsTheOrdersOfTheSignedInTechnician() {
        var appointments = mock(AppointmentRepository.class);
        var hcm = mock(ExternalHcmService.class);
        var queries = new AppointmentQueryServiceImpl(appointments, mock(EvaluationSlotHoldRepository.class), hcm,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(hcm.fetchTechnicianIdOfUser(70L)).thenReturn(Optional.of(7L));
        when(hcm.fetchTechnicianIdOfUser(71L)).thenReturn(Optional.empty());
        var evaluation = order(ServiceType.EVALUATION, 1L);
        when(appointments.findByTechnicianIdOrderByScheduledDateAscStartTimeAsc(7L)).thenReturn(List.of(evaluation));

        assertEquals(List.of(evaluation), queries.handle(new GetTechnicianOrdersQuery(70L)));
        assertTrue(queries.handle(new GetTechnicianOrdersQuery(71L)).isEmpty());
        assertEquals(Optional.of(7L), queries.handle(new GetTechnicianIdByUserIdQuery(70L)));

        var facade = mock(HcmContextFacade.class);
        when(facade.fetchTechnicianIdOfUser(70L)).thenReturn(Optional.of(7L));
        assertEquals(Optional.of(7L), new ExternalHcmService(facade).fetchTechnicianIdOfUser(70L));
    }

    @Test
    void hcmFindsTheActiveTechnicianOfAnAccount() {
        var employees = mock(EmployeeRepository.class);
        var technician = employee(7L, 70L, EmployeeRole.TECH);
        var manager = employee(8L, 80L, EmployeeRole.SUBSCRIPTION_MANAGER);
        var otherBusiness = new Employee(2L, 90L, 90L, EmployeeRole.TECH);
        ReflectionTestUtils.setField(otherBusiness, "id", 9L);
        when(employees.findAll()).thenReturn(List.of(manager, otherBusiness, technician));
        var facade = new HcmContextFacadeImpl(employees, mock(ContractPersistenceRepository.class),
                mock(WorkSchedulePersistenceRepository.class), mock(ExternalProfilesService.class), 1L);

        assertEquals(Optional.of(7L), facade.fetchTechnicianIdOfUser(70L));
        assertTrue(facade.fetchTechnicianIdOfUser(80L).isEmpty());
        assertTrue(facade.fetchTechnicianIdOfUser(90L).isEmpty());
        assertTrue(facade.fetchTechnicianIdOfUser(null).isEmpty());
    }

    @Test
    void subscriptionsReleasesTheHeldPaymentAndConfirmsAtTheHome() {
        var drafts = mock(ConfigurationDraftRepository.class);
        var gateway = mock(PaymentAuthorizationGateway.class);
        var facade = new SubscriptionsContextFacadeImpl(drafts, mock(SubscriptionRepository.class), gateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var held = mock(ConfigurationDraft.class);
        when(held.getPaymentStatus()).thenReturn(PaymentStatus.PREAUTHORIZED);
        when(held.getStripePaymentIntentId()).thenReturn("pi_123");
        var canceled = mock(ConfigurationDraft.class);
        when(canceled.getPaymentStatus()).thenReturn(PaymentStatus.CANCELED);
        var withoutIntent = mock(ConfigurationDraft.class);
        when(withoutIntent.getPaymentStatus()).thenReturn(PaymentStatus.PREAUTHORIZED);
        when(drafts.findById("held")).thenReturn(Optional.of(held));
        when(drafts.findById("canceled")).thenReturn(Optional.of(canceled));
        when(drafts.findById("no-intent")).thenReturn(Optional.of(withoutIntent));
        when(drafts.findById("missing")).thenReturn(Optional.empty());

        assertTrue(facade.releaseConfigurationDraftPayment("held"));
        verify(gateway).cancel(new StripePaymentIntentId("pi_123"));
        verify(held).markPreauthorizationCanceled();
        verify(drafts).save(held);
        assertFalse(facade.releaseConfigurationDraftPayment("canceled"));
        assertFalse(facade.releaseConfigurationDraftPayment("no-intent"));
        assertFalse(facade.releaseConfigurationDraftPayment("missing"));
        assertFalse(facade.releaseConfigurationDraftPayment(" "));
        assertFalse(facade.releaseConfigurationDraftPayment(null));
        verify(gateway, times(1)).cancel(any());

        var subscriptions = mock(SubscriptionsContextFacade.class);
        when(subscriptions.releaseConfigurationDraftPayment("d1")).thenReturn(true);
        var service = new ExternalSubscriptionsService(subscriptions);
        assertTrue(service.releasePaymentOf("d1"));
    }

    @Test
    void theTechnicianAttendsOnlyTheOrdersAssignedToThem() throws Exception {
        var commands = mock(AppointmentCommandService.class);
        var queries = mock(AppointmentQueryService.class);
        var mvc = mvc(commands, queries);
        var mine = order(ServiceType.INSTALLATION, 2L);
        var someoneElses = new Appointment("draft", ServiceType.EVALUATION, null, DAY,
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)), 8L, "Luis Rojas", null, null, null, NOW);
        ReflectionTestUtils.setField(someoneElses, "id", 3L);
        when(queries.handle(new GetTechnicianIdByUserIdQuery(70L))).thenReturn(Optional.of(7L));
        when(queries.handle(new GetTechnicalOrderByIdQuery(2L))).thenReturn(Optional.of(mine));
        when(queries.handle(new GetTechnicalOrderByIdQuery(3L))).thenReturn(Optional.of(someoneElses));
        when(queries.handle(new GetTechnicianOrdersQuery(70L))).thenReturn(List.of(mine));

        mvc.perform(get("/api/v1/technical-orders/mine").principal(TECHNICIAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ORD-2026-0002"))
                .andExpect(jsonPath("$[0].serviceType").value("INSTALLATION"));
        mvc.perform(get("/api/v1/technical-orders/2").principal(TECHNICIAN)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/technical-orders/3").principal(TECHNICIAN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/technical-orders/9").principal(TECHNICIAN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/technical-orders/3").principal(ADMIN)).andExpect(status().isOk());
        var stranger = new UsernamePasswordAuthenticationToken("99", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN")));
        when(queries.handle(new GetTechnicianIdByUserIdQuery(99L))).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/technical-orders/2").principal(stranger)).andExpect(status().isForbidden());
    }

    @Test
    void theAppStartsTheVisitRegistersDevicesAndInstallsInTheSameVisit() throws Exception {
        var commands = mock(AppointmentCommandService.class);
        var queries = mock(AppointmentQueryService.class);
        var mvc = mvc(commands, queries);
        var installation = order(ServiceType.INSTALLATION, 2L);
        installation.start(NOW);
        when(queries.handle(new GetTechnicianIdByUserIdQuery(70L))).thenReturn(Optional.of(7L));
        when(queries.handle(new GetTechnicalOrderByIdQuery(1L))).thenReturn(Optional.of(order(ServiceType.EVALUATION, 1L)));
        when(queries.handle(new GetTechnicalOrderByIdQuery(2L))).thenReturn(Optional.of(installation));
        when(commands.handle(new StartTechnicalOrderCommand(2L))).thenReturn(installation);
        var withDevice = order(ServiceType.INSTALLATION, 2L);
        withDevice.registerDevice(microphone("00:1A:2B:3C:4D:5E"));
        when(commands.handle(new RegisterInstalledDeviceCommand(2L, "Micrófono Cabecera", "00:1A:2B:3C:4D:5E",
                InstalledDeviceType.MICROPHONE))).thenReturn(withDevice);
        when(commands.handle(new RemoveInstalledDeviceCommand(2L, "00:1A:2B:3C:4D:5E"))).thenReturn(installation);
        var evaluated = order(ServiceType.EVALUATION, 1L);
        evaluated.recordEvaluationResult(EvaluationResult.VIABLE, null, NOW);
        when(commands.handle(new RecordEvaluationResultCommand(1L, EvaluationResult.VIABLE, null))).thenReturn(evaluated);
        var installed = order(ServiceType.INSTALLATION, 2L);
        installed.recordInstallationResult(InstallationResult.APPROVED, null, NOW);
        when(commands.handle(new RecordInstallationResultCommand(2L, InstallationResult.APPROVED, null))).thenReturn(installed);

        mvc.perform(post("/api/v1/technical-orders/2/start").principal(TECHNICIAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").exists());
        mvc.perform(post("/api/v1/technical-orders/2/devices").principal(TECHNICIAN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"Micrófono Cabecera","macAddress":"00:1A:2B:3C:4D:5E","type":"MICROPHONE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installedDevices[0].macAddress").value("00:1A:2B:3C:4D:5E"))
                .andExpect(jsonPath("$.installedDevices[0].type").value("MICROPHONE"));
        mvc.perform(post("/api/v1/technical-orders/2/devices").principal(TECHNICIAN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/technical-orders/2/devices/00:1A:2B:3C:4D:5E").principal(TECHNICIAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installedDevices").isEmpty());
        mvc.perform(post("/api/v1/technical-orders/1/evaluation-result").principal(TECHNICIAN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"result":"VIABLE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").exists());
        mvc.perform(post("/api/v1/technical-orders/2/installation-result").principal(TECHNICIAN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"result":"APPROVED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationResult").value("APPROVED"));
        when(commands.handle(new RegisterInstalledDeviceCommand(2L, "Otro", "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB)))
                .thenThrow(new InstalledDeviceAlreadyRegistered("00:1A:2B:3C:4D:5E"));
        mvc.perform(post("/api/v1/technical-orders/2/devices").principal(TECHNICIAN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"Otro","macAddress":"00:1A:2B:3C:4D:5E","type":"HUB"}"""))
                .andExpect(status().isConflict());
    }

    private static MockMvc mvc(AppointmentCommandService commands, AppointmentQueryService queries) {
        return MockMvcBuilders.standaloneSetup(new TechnicalOrdersController(commands, queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static AppointmentCommandServiceImpl commands(AppointmentRepository appointments,
                                                          ExternalSubscriptionsService subscriptions,
                                                          IncidentPersistenceRepository incidents) {
        return new AppointmentCommandServiceImpl(appointments, mock(EvaluationSlotHoldRepository.class), incidents,
                mock(ExternalHcmService.class), subscriptions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Employee employee(Long id, Long userId, EmployeeRole role) {
        var employee = new Employee(1L, userId, userId, role);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
