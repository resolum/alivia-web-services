package com.resolum.alivia.communications.application.acl;

import com.resolum.alivia.communications.domain.model.commands.CreateNotificationCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.repositories.NotificationRepository;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationsContextFacadeImplTest {
    private final NotificationCommandService commands = mock(NotificationCommandService.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationsContextFacadeImpl facade = new NotificationsContextFacadeImpl(commands, repository);

    @Test
    void createsTheNotificationOfAnotherContext() {
        facade.notifyUser(70L, "ASSIGNMENT_CREATED", "15", "Nueva orden asignada", "Instalación");

        verify(commands).handle(new CreateNotificationCommand(70L, NotificationType.ASSIGNMENT_CREATED, "15",
                "Nueva orden asignada", "Instalación"));
    }

    @Test
    void notifiesOnceAboutTheSameSource() {
        when(repository.existsByRecipientIdAndTypeAndSourceId(new UserId(5L), NotificationType.CARE_ACTIVITY_REMINDER, "a-1"))
                .thenReturn(true);

        assertFalse(facade.notifyUserOnce(5L, "CARE_ACTIVITY_REMINDER", "a-1", "Recordatorio", "Paseo"));
        assertTrue(facade.notifyUserOnce(5L, "CARE_ACTIVITY_REMINDER", "a-2", "Recordatorio", "Paseo"));

        verify(commands).handle(new CreateNotificationCommand(5L, NotificationType.CARE_ACTIVITY_REMINDER, "a-2",
                "Recordatorio", "Paseo"));
    }

    @Test
    void rejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> facade.notifyUser(1L, "UNKNOWN", "1", "T", "B"));
        assertThrows(IllegalArgumentException.class, () -> facade.notifyUserOnce(1L, null, "1", "T", "B"));
        verify(commands, never()).handle(any(CreateNotificationCommand.class));
    }
}
