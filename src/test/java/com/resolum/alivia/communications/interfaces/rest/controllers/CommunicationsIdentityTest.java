package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.exceptions.CallerNotIdentified;
import com.resolum.alivia.communications.domain.model.commands.CreateAlertCommand;
import com.resolum.alivia.communications.domain.model.commands.RegisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.commands.UnregisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.queries.GetAllAlertsByUserIdQuery;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.communications.domain.services.AlertCommandService;
import com.resolum.alivia.communications.domain.services.AlertQueryService;
import com.resolum.alivia.communications.domain.services.PushDeviceCommandService;
import com.resolum.alivia.communications.interfaces.rest.resources.RegisterPushDeviceResource;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The user of alerts and push devices is always the authenticated one; the admin-only endpoints say so. */
class CommunicationsIdentityTest {

    private final Principal caller = () -> "7";

    @Test
    void alertsAreListedForTheCallerOnly() {
        var queries = mock(AlertQueryService.class);
        when(queries.handle(new GetAllAlertsByUserIdQuery(7L))).thenReturn(List.of());
        var controller = new AlertsController(mock(AlertCommandService.class), queries);

        assertEquals(0, controller.getAll(caller).getBody().size());
        verify(queries).handle(new GetAllAlertsByUserIdQuery(7L));
        assertThrows(CallerNotIdentified.class, () -> controller.getAll(null));
    }

    @Test
    void creatingAnAlertIsAdministratorOnlyAndTargetsTheRequestedUser() throws Exception {
        var commands = mock(AlertCommandService.class);
        when(commands.handle(any(CreateAlertCommand.class))).thenReturn(1L);
        var controller = new AlertsController(commands, mock(AlertQueryService.class));

        controller.createAlert(55L, "help-1", "t", "b", caller);

        verify(commands).handle(new CreateAlertCommand(55L, "help-1", "t", "b"));
        assertAdministratorOnly(AlertsController.class.getMethod("createAlert", Long.class, String.class,
                String.class, String.class, Principal.class));
    }

    @Test
    void aPushTokenIsRegisteredAndRemovedForTheCallerNeverForTheUserOfTheBody() {
        var commands = mock(PushDeviceCommandService.class);
        when(commands.handle(any(RegisterPushDeviceCommand.class))).thenReturn(1L);
        var controller = new PushDevicesController(commands);

        controller.register(new RegisterPushDeviceResource(99L, "token-abc", "ios"), caller);
        controller.unregister("token-abc", caller);

        verify(commands).handle(new RegisterPushDeviceCommand(7L, "token-abc", PushPlatform.IOS));
        verify(commands).handle(new UnregisterPushDeviceCommand(7L, "token-abc"));
        assertThrows(CallerNotIdentified.class, () -> controller.unregister("token-abc", () -> "edge:3"));
        assertThrows(CallerNotIdentified.class,
                () -> controller.register(new RegisterPushDeviceResource(99L, "token-abc", "ios"), null));
    }

    @Test
    void theEmailLogAndNotificationCreationAreAdministratorOnly() throws Exception {
        assertAdministratorOnly(EmailsController.class.getMethod("getAll", String.class));
        assertAdministratorOnly(NotificationsController.class.getMethod("createNotification",
                com.resolum.alivia.communications.interfaces.rest.resources.CreateNotificationResource.class,
                Principal.class));
        verifyNoInteractions(mock(PushDeviceCommandService.class));
    }

    private static void assertAdministratorOnly(Method endpoint) {
        PreAuthorize rule = endpoint.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('ADMINISTRATOR')", rule == null ? null : rule.value(), endpoint.getName());
    }
}
