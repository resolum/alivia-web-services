package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.acl.AssetsContextFacadeImpl;
import com.resolum.alivia.assets.application.internal.commandservices.DeviceNetworkCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.AddDeviceToNetworkCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByMacAddressQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.assets.interfaces.rest.controllers.DevicesController;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgesController;
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
 * Assets facade answers other contexts and what the not-yet-built network operations do.
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
            return new EdgesController(mock(EdgeCommandService.class), mock(EdgeQueryService.class));
        }
    }

    private static final RegisterDeviceResource REGISTER = new RegisterDeviceResource("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT");
    private static final UpdateDeviceResource UPDATE = new UpdateDeviceResource("AA:BB:CC:DD:EE:FF", "LIGHT");
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
            }
        }
    }

    @Test
    void theFacadeAnswersEmptyInsteadOfIdZeroWhenTheMacIsUnknown() {
        var devices = mock(DeviceQueryService.class);
        var edges = mock(EdgeQueryService.class);
        var facade = new AssetsContextFacadeImpl(devices, mock(DeviceCommandService.class),
                mock(EdgeCommandService.class), edges);
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
    void networkOperationsFailLoudlyUntilTheyAreImplemented() {
        var service = new DeviceNetworkCommandServiceImpl(mock(DeviceNetworkRepository.class));

        assertThrows(UnsupportedOperationException.class, () -> service.handle(new AddDeviceToNetworkCommand(1L)));
    }
}
