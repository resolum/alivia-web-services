package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.acl.AssetsContextFacadeImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByMacAddressQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.rest.controllers.DevicesController;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeCredentialsController;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgesController;
import com.resolum.alivia.assets.interfaces.rest.controllers.InstallationEdgesController;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.interfaces.rest.resources.RegisterDeviceResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateDeviceResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateEdgeResource;
import com.resolum.alivia.business.interfaces.acl.BusinessContextFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Who may call the inventory endpoints (the real method security of the application), how the
 * Assets facade answers other contexts.
 */
class AssetsAccessAndFacadeTest {

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean
        DevicesController devicesController() {
            var commands = mock(DeviceCommandService.class);
            var queries = mock(DeviceQueryService.class);
            when(commands.handle(any(RegisterDeviceCommand.class))).thenReturn(7L);
            var device = new Device("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT", 1L);
            ReflectionTestUtils.setField(device, "id", 7L);
            when(queries.handle(any(com.resolum.alivia.assets.domain.model.queries.GetDeviceByIdQuery.class)))
                    .thenReturn(Optional.of(device));
            return new DevicesController(commands, queries, 1L);
        }

        @Bean
        EdgesController edgesController() {
            var commands = mock(EdgeCommandService.class);
            var queries = mock(EdgeQueryService.class);
            when(commands.handle(any(com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand.class))).thenReturn(3L);
            when(commands.handle(any(com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand.class))).thenReturn(3L);
            var edge = new Edge(1L, "AA:BB:CC:DD:EE:FF", "ACTUATOR_EDGE");
            ReflectionTestUtils.setField(edge, "id", 3L);
            when(queries.handle(any(com.resolum.alivia.assets.domain.model.queries.GetEdgeByIdQuery.class)))
                    .thenReturn(Optional.of(edge));
            return new EdgesController(commands, queries, 1L);
        }

        @Bean
        InstallationEdgesController installationEdgesController() {
            var commands = mock(HouseholdDeviceRegistryCommandService.class);
            var view = new InstallationEdges(3L, null, null, null);
            when(commands.handle(any(PlaceEdgeInInstallationCommand.class))).thenReturn(view);
            when(commands.handle(any(RemoveEdgeFromInstallationCommand.class))).thenReturn(view);
            return new InstallationEdgesController(commands);
        }

        @Bean
        EdgeCredentialsController edgeCredentialsController() {
            var commands = mock(EdgeCredentialCommandService.class);
            when(commands.handle(any(IssueEdgeCredentialCommand.class)))
                    .thenReturn(new IssuedEdgeCredential(7L, EdgeType.ACTUATOR_EDGE, "7.secret"));
            return new EdgeCredentialsController(commands);
        }
    }

    private static final RegisterDeviceResource REGISTER = new RegisterDeviceResource("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT");
    private static final UpdateDeviceResource UPDATE = new UpdateDeviceResource("AA:BB:CC:DD:EE:FF", "LIGHT");
    private static final com.resolum.alivia.assets.interfaces.rest.resources.RegisterEdgeResource REGISTER_EDGE =
            new com.resolum.alivia.assets.interfaces.rest.resources.RegisterEdgeResource("AA:BB:CC:DD:EE:FF", "ACTUATOR_EDGE");
    private static final com.resolum.alivia.assets.interfaces.rest.resources.ChangeEdgeStatusResource EDGE_STATUS =
            new com.resolum.alivia.assets.interfaces.rest.resources.ChangeEdgeStatusResource("MAINTENANCE");
    private static final UpdateEdgeResource UPDATE_EDGE = new UpdateEdgeResource("AA:BB:CC:DD:EE:FF", "ACTUATOR_EDGE");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(SecurityConfig.class);
    }

    private static void signInAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("9", "n/a", role));
    }

    @Test
    void administratorsMayManageTheInventory() {
        try (var context = context()) {
            signInAs("ROLE_ADMINISTRATOR");

            assertEquals(7L, context.getBean(DevicesController.class).registerDevice(REGISTER).id());
            assertDoesNotThrow(() -> context.getBean(DevicesController.class).updateDevice(7L, UPDATE));
            assertDoesNotThrow(() -> context.getBean(EdgesController.class).updateEdge(3L, UPDATE_EDGE));
            assertEquals(3L, context.getBean(EdgesController.class).registerEdge(REGISTER_EDGE).id());
            assertEquals(3L, context.getBean(EdgesController.class).changeEdgeStatus(3L, EDGE_STATUS).id());
            assertDoesNotThrow(() -> context.getBean(InstallationEdgesController.class).placeEdge(3L, 7L));
            assertDoesNotThrow(() -> context.getBean(InstallationEdgesController.class).removeEdge(3L, 7L));
            assertDoesNotThrow(() -> context.getBean(EdgeCredentialsController.class).issueCredential(7L));
            assertDoesNotThrow(() -> context.getBean(EdgeCredentialsController.class).revokeCredential(7L));
        }
    }

    @Test
    void everyOtherRoleIsDeniedOnEveryInventoryOperation() {
        try (var context = context()) {
            for (var role : new String[]{"ROLE_CAREGIVER", "ROLE_TECHNICIAN", "ROLE_SUBSCRIPTION_MANAGER"}) {
                signInAs(role);

                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(DevicesController.class).registerDevice(REGISTER), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(DevicesController.class).updateDevice(7L, UPDATE), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(EdgesController.class).updateEdge(3L, UPDATE_EDGE), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(EdgesController.class).registerEdge(REGISTER_EDGE), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(EdgesController.class).changeEdgeStatus(3L, EDGE_STATUS), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(InstallationEdgesController.class).placeEdge(3L, 7L), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(InstallationEdgesController.class).removeEdge(3L, 7L), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(EdgeCredentialsController.class).issueCredential(7L), role);
                assertThrows(AccessDeniedException.class,
                        () -> context.getBean(EdgeCredentialsController.class).revokeCredential(7L), role);
            }
        }
    }

    @Test
    void theFacadeAnswersEmptyInsteadOfIdZeroWhenTheMacIsUnknown() {
        var devices = mock(DeviceQueryService.class);
        var edges = mock(EdgeQueryService.class);
        var facade = new AssetsContextFacadeImpl(devices, mock(DeviceCommandService.class),
                mock(EdgeCommandService.class), edges, mock(HouseholdDeviceRegistryCommandService.class), mock(DeviceAssignmentCommandService.class));
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        var edge = new Edge(1L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "id", 3L);
        when(devices.handle(new GetDeviceByMacAddressQuery("aa-bb-cc-dd-ee-ff"))).thenReturn(Optional.of(device));
        when(devices.handle(new GetDeviceByMacAddressQuery("AA:BB:CC:DD:EE:99"))).thenReturn(Optional.empty());
        when(edges.handle(new GetEdgeByMacAddressQuery("AA:BB:CC:DD:EE:00"))).thenReturn(Optional.of(edge));
        when(edges.handle(new GetEdgeByMacAddressQuery("AA:BB:CC:DD:EE:99"))).thenReturn(Optional.empty());

        assertEquals(Optional.of(7L), facade.fetchDeviceByMacAddress("aa-bb-cc-dd-ee-ff"));
        assertEquals(Optional.empty(), facade.fetchDeviceByMacAddress("AA:BB:CC:DD:EE:99"));
        assertEquals(Optional.of(3L), facade.fetchEdgeByMacAddress("AA:BB:CC:DD:EE:00"));
        assertEquals(Optional.empty(), facade.fetchEdgeByMacAddress("AA:BB:CC:DD:EE:99"));
    }

    @Test
    void theBusinessServiceOnlyConfirmsBusinessesThatExist() {
        var businesses = mock(BusinessContextFacade.class);
        when(businesses.existsBusinessById(1L)).thenReturn(true);
        var service = new ExternalBusinessService(businesses);

        assertTrue(service.existsBusiness(1L));
        assertFalse(service.existsBusiness(2L));
        assertFalse(service.existsBusiness(null));
        verify(businesses, never()).existsBusinessById(null);
    }

    @Test
    void theFacadeRegistersInstallationsThroughTheRegistryService() {
        var registryService = mock(HouseholdDeviceRegistryCommandService.class);
        when(registryService.handle(any(RegisterInstallationCommand.class))).thenReturn(11L);
        var facade = new AssetsContextFacadeImpl(mock(DeviceQueryService.class), mock(DeviceCommandService.class),
                mock(EdgeCommandService.class), mock(EdgeQueryService.class), registryService, mock(DeviceAssignmentCommandService.class));

        assertEquals(11L, facade.registerInstallation(3L, 5L, 1L));

        verify(registryService).handle(new RegisterInstallationCommand(3L, 5L, 1L));
    }

    @Test
    void theFacadePlacesAndRemovesDevicesOnBehalfOfAContextThatAlreadyAuthorizedTheCaller() {
        var assignments = mock(DeviceAssignmentCommandService.class);
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT", 1L);
        var assignment = device.assignTo(3L, 5L, "sala", "Luz", 6L, java.time.LocalDateTime.of(2026, 11, 5, 10, 0));
        ReflectionTestUtils.setField(assignment, "id", 21L);
        when(assignments.handle(any(AssignDeviceToInstallationCommand.class))).thenReturn(assignment);
        var facade = new AssetsContextFacadeImpl(mock(DeviceQueryService.class), mock(DeviceCommandService.class),
                mock(EdgeCommandService.class), mock(EdgeQueryService.class), mock(HouseholdDeviceRegistryCommandService.class), assignments);

        assertEquals(21L, facade.assignDevice(3L, 7L, "sala", "Luz", 6L));
        facade.unassignDevice(7L, "Fin de la visita", 6L);

        verify(assignments).handle(new AssignDeviceToInstallationCommand(3L, 7L, "sala", "Luz", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT));
        verify(assignments).handle(new UnassignDeviceCommand(7L, "Fin de la visita", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT));
    }
}
