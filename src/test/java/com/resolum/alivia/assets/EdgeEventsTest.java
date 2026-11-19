package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeEventCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.ReportEdgeEventCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeEventType;
import com.resolum.alivia.assets.domain.services.EdgeEventCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeEventsController;
import com.resolum.alivia.assets.interfaces.rest.resources.ReportEdgeEventResource;
import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdMembershipFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EdgeEventsTest {
    private final HouseholdDeviceRegistryRepository registries = mock(HouseholdDeviceRegistryRepository.class);
    private final HouseholdMembershipFacade memberships = mock(HouseholdMembershipFacade.class);
    private final NotificationsContextFacade notifications = mock(NotificationsContextFacade.class);
    private final EdgeEventCommandServiceImpl service = new EdgeEventCommandServiceImpl(registries,
            new ExternalNotificationsService(memberships, notifications));

    @BeforeEach
    void home() {
        var registry = mock(HouseholdDeviceRegistry.class);
        when(registry.isActive()).thenReturn(true);
        when(registry.getHouseholdId()).thenReturn(7L);
        var door = mock(Device.class);
        when(door.getAlias()).thenReturn("Puerta principal");
        var assignment = mock(DeviceAssignment.class);
        when(assignment.getDevice()).thenReturn(door);
        when(registry.activeAssignmentOf(10L)).thenReturn(Optional.of(assignment));
        when(registry.activeAssignmentOf(99L)).thenReturn(Optional.empty());
        when(registries.findByAssignedEdgeId(3L)).thenReturn(Optional.of(registry));
        when(memberships.fetchActivePrincipalUserIds(7L)).thenReturn(Optional.of(Set.of(8L)));
        when(notifications.notifyUserOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void aHelpRequestReachesTheRelativeInCharge() {
        assertEquals(1, service.handle(new ReportEdgeEventCommand(3L, "help-1", EdgeEventType.HELP_REQUESTED, null, null,
                " me caí, ayuda ")));

        verify(notifications).notifyUserOnce(8L, "HELP_REQUESTED", "edge-help-1", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa: «me caí, ayuda».");
    }

    @Test
    void aHelpRequestWithoutWords() {
        service.handle(new ReportEdgeEventCommand(3L, "help-2", EdgeEventType.HELP_REQUESTED, null, null, null));

        verify(notifications).notifyUserOnce(8L, "HELP_REQUESTED", "edge-help-2", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa.");
    }

    @Test
    void namesTheDeviceThatFailedOrRunsOutOfBattery() {
        service.handle(new ReportEdgeEventCommand(3L, "f-1", EdgeEventType.DEVICE_FAILURE, 10L, null, "sin respuesta del embebido"));
        service.handle(new ReportEdgeEventCommand(3L, "f-2", EdgeEventType.DEVICE_FAILURE, 99L, null, null));
        service.handle(new ReportEdgeEventCommand(3L, "b-1", EdgeEventType.LOW_BATTERY, 10L, 15, null));
        service.handle(new ReportEdgeEventCommand(3L, "b-2", EdgeEventType.LOW_BATTERY, null, null, null));

        verify(notifications).notifyUserOnce(8L, "ACTUATOR_FAILURE", "edge-f-1", "Falla en un dispositivo",
                "Puerta principal no respondió: sin respuesta del embebido.");
        verify(notifications).notifyUserOnce(8L, "ACTUATOR_FAILURE", "edge-f-2", "Falla en un dispositivo",
                "Un dispositivo de la casa no respondió.");
        verify(notifications).notifyUserOnce(8L, "LOW_BATTERY", "edge-b-1", "Batería baja",
                "Puerta principal tiene 15% de batería.");
        verify(notifications).notifyUserOnce(8L, "LOW_BATTERY", "edge-b-2", "Batería baja",
                "Un dispositivo de la casa tiene poca batería.");
    }

    @Test
    void aRepeatedEventNotifiesNobodyAgain() {
        when(notifications.notifyUserOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertEquals(0, service.handle(new ReportEdgeEventCommand(3L, "help-1", EdgeEventType.HELP_REQUESTED, null, null, null)));
    }

    @Test
    void anEdgeOutsideAnActiveHomeIsRefused() {
        when(registries.findByAssignedEdgeId(4L)).thenReturn(Optional.empty());

        assertThrows(EdgeWithoutInstallation.class,
                () -> service.handle(new ReportEdgeEventCommand(4L, "x", EdgeEventType.HELP_REQUESTED, null, null, null)));
    }

    @Test
    void aHouseholdWithoutRelativesNotifiesNobody() {
        when(memberships.fetchActivePrincipalUserIds(7L)).thenReturn(Optional.empty());

        assertEquals(0, service.handle(new ReportEdgeEventCommand(3L, "x", EdgeEventType.HELP_REQUESTED, null, null, null)));
        verify(notifications, never()).notifyUserOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void theCommandChecksWhatTheEdgeSends() {
        assertThrows(IllegalArgumentException.class, () -> new ReportEdgeEventCommand(null, "x", EdgeEventType.LOW_BATTERY, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ReportEdgeEventCommand(3L, "con espacios", EdgeEventType.LOW_BATTERY, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ReportEdgeEventCommand(3L, "x", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ReportEdgeEventCommand(3L, "x", EdgeEventType.LOW_BATTERY, null, 101, null));
        assertNull(new ReportEdgeEventCommand(3L, "x", EdgeEventType.LOW_BATTERY, null, 5, "  ").detail());
        assertEquals(200, new ReportEdgeEventCommand(3L, "x", EdgeEventType.HELP_REQUESTED, null, null, "a".repeat(300)).detail().length());
    }

    @Test
    void theControllerReportsAsTheAuthenticatedEdge() {
        var events = mock(EdgeEventCommandService.class);
        when(events.handle(new ReportEdgeEventCommand(3L, "help-9", EdgeEventType.HELP_REQUESTED, null, null, "ayuda"))).thenReturn(1);
        var auth = new UsernamePasswordAuthenticationToken("edge:3", null, List.of());

        var response = new EdgeEventsController(events).report(
                new ReportEdgeEventResource("help-9", "help_requested", null, null, "ayuda"), auth);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(1, response.getBody().notified());
        assertThrows(IllegalArgumentException.class, () -> new EdgeEventsController(events).report(
                new ReportEdgeEventResource("x", "FIRE", null, null, null), auth));
    }
}
