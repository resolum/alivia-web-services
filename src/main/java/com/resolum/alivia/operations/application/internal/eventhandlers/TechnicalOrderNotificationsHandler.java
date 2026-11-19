package com.resolum.alivia.operations.application.internal.eventhandlers;

import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.operations.domain.model.events.TechnicalOrderAssignedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifies the technician once the change of the order is committed. The notification is saved in its own
 * transaction, and a failure only loses the notification, never the change of the order.
 */
@Slf4j
@Component
public class TechnicalOrderNotificationsHandler {
    private final ExternalNotificationsService notifications;

    public TechnicalOrderNotificationsHandler(ExternalNotificationsService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TechnicalOrderAssignedEvent event) {
        try {
            notifications.notifyTechnician(event.order(), event.change());
        } catch (RuntimeException e) {
            log.warn("Could not notify the technician of order {}: {}", event.order().getId(), e.getMessage());
        }
    }
}
