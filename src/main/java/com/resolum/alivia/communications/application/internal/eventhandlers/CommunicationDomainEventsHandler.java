package com.resolum.alivia.communications.application.internal.eventhandlers;

import com.resolum.alivia.communications.domain.model.commands.SendAlertCommand;
import com.resolum.alivia.communications.domain.model.commands.SendPushNotificationCommand;
import com.resolum.alivia.communications.domain.model.events.AlertCreatedEvent;
import com.resolum.alivia.communications.domain.model.events.NotificationCreatedEvent;
import com.resolum.alivia.communications.domain.services.AlertCommandService;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class CommunicationDomainEventsHandler {

    private final AlertCommandService alertCommandService;
    private final NotificationCommandService notificationCommandService;

    public CommunicationDomainEventsHandler(AlertCommandService alertCommandService,
                                           NotificationCommandService notificationCommandService) {
        this.alertCommandService = alertCommandService;
        this.notificationCommandService = notificationCommandService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AlertCreatedEvent event) {
        Long id = event.getAlertId();
        if (id != null) {
            try {
                alertCommandService.handle(new SendAlertCommand(id));
            } catch (Exception e) {
                log.error("Failed to trigger SendAlertCommand for alert {}: {}", id, e.getMessage());
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationCreatedEvent event) {
        Long id = event.getNotificationId();
        if (id != null) {
            try {
                notificationCommandService.handle(new SendPushNotificationCommand(id));
            } catch (Exception e) {
                log.error("Failed to trigger SendPushNotificationCommand for notification {}: {}", id, e.getMessage());
            }
        }
    }
}
