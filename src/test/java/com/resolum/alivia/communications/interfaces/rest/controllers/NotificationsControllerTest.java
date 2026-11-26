package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.exceptions.CallerNotIdentified;
import com.resolum.alivia.communications.domain.exceptions.NotificationNotFound;
import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.commands.MarkNotificationAsReadCommand;
import com.resolum.alivia.communications.domain.model.queries.GetAllNotificationsByUserIdQuery;
import com.resolum.alivia.communications.domain.model.queries.GetNotificationByIdQuery;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import com.resolum.alivia.communications.domain.services.NotificationQueryService;
import com.resolum.alivia.communications.interfaces.rest.resources.MarkNotificationsReadResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationsControllerTest {

    private NotificationCommandService commandService;
    private NotificationQueryService queryService;
    private NotificationsController controller;

    @BeforeEach
    void setUp() {
        commandService = mock(NotificationCommandService.class);
        queryService = mock(NotificationQueryService.class);
        controller = new NotificationsController(commandService, queryService);
    }

    private static Notification notificationOf(long userId) {
        return new Notification(new UserId(userId), NotificationType.LOW_BATTERY, "dev-01",
                "Batería baja", "Sensor con 10% de carga");
    }

    @Test
    void getAllReturnsTheNotificationsOfTheAuthenticatedUserOnly() {
        when(queryService.handle(new GetAllNotificationsByUserIdQuery(1L, null)))
                .thenReturn(List.of(notificationOf(1L)));

        Principal principal = () -> "1";
        var response = controller.getAll(principal, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Batería baja", response.getBody().get(0).title());
        verify(queryService, only()).handle(new GetAllNotificationsByUserIdQuery(1L, null));
    }

    @Test
    void getByIdReturnsTheNotificationOfTheCaller() {
        when(queryService.handle(new GetNotificationByIdQuery(100L))).thenReturn(Optional.of(notificationOf(1L)));

        Principal principal = () -> "1";
        var response = controller.getById(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("dev-01", response.getBody().sourceId());
    }

    @Test
    void getByIdHidesTheNotificationOfAnotherUser() {
        when(queryService.handle(new GetNotificationByIdQuery(100L))).thenReturn(Optional.of(notificationOf(1L)));

        Principal other = () -> "2";
        assertThrows(NotificationNotFound.class, () -> controller.getById(100L, other));
    }

    @Test
    void markAsReadActsForTheCallerNeverForAnotherUser() {
        when(queryService.handle(new GetNotificationByIdQuery(100L))).thenReturn(Optional.of(notificationOf(2L)));

        Principal principal = () -> "2";
        controller.markAsRead(100L, principal);
        controller.markMultipleAsRead(new MarkNotificationsReadResource(List.of(100L, 101L)), principal);

        verify(commandService, times(2)).handle(new MarkNotificationAsReadCommand(100L, 2L));
        verify(commandService).handle(new MarkNotificationAsReadCommand(101L, 2L));
    }

    @Test
    void everyEndpointRefusesACallerWhoIsNotAUserAccount() {
        Principal edge = () -> "edge:5";

        assertThrows(CallerNotIdentified.class, () -> controller.getAll(null, null));
        assertThrows(CallerNotIdentified.class, () -> controller.getAll(edge, null));
        assertThrows(CallerNotIdentified.class, () -> controller.getById(1L, edge));
        assertThrows(CallerNotIdentified.class, () -> controller.markAsRead(1L, edge));
        assertThrows(CallerNotIdentified.class,
                () -> controller.markMultipleAsRead(new MarkNotificationsReadResource(List.of(1L)), null));
        verifyNoInteractions(commandService);
        verify(queryService, never()).handle(any(GetAllNotificationsByUserIdQuery.class));
    }
}
