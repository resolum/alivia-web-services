package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.queries.GetAllNotificationsByUserIdQuery;
import com.resolum.alivia.communications.domain.model.queries.GetNotificationByIdQuery;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import com.resolum.alivia.communications.domain.services.NotificationQueryService;
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

    @Test
    void getAllReturnsNotificationResources() {
        Notification notification = new Notification(
                new UserId(1L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor con 10% de carga"
        );
        when(queryService.handle(any(GetAllNotificationsByUserIdQuery.class)))
                .thenReturn(List.of(notification));

        Principal principal = () -> "1";
        var response = controller.getAll(principal, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Batería baja", response.getBody().get(0).title());
    }

    @Test
    void getByIdReturnsNotification() {
        Notification notification = new Notification(
                new UserId(1L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor con 10% de carga"
        );
        when(queryService.handle(new GetNotificationByIdQuery(100L)))
                .thenReturn(Optional.of(notification));

        var response = controller.getById(100L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("dev-01", response.getBody().sourceId());
    }
}
