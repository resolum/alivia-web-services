package com.resolum.alivia.communications.application.internal.tasks;

import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredAlertsCommand;
import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredPushNotificationsCommand;
import com.resolum.alivia.communications.domain.services.AlertCommandService;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommunicationRetryScheduler {

    private final AlertCommandService alertCommandService;
    private final NotificationCommandService notificationCommandService;

    public CommunicationRetryScheduler(AlertCommandService alertCommandService,
                                      NotificationCommandService notificationCommandService) {
        this.alertCommandService = alertCommandService;
        this.notificationCommandService = notificationCommandService;
    }

    @Scheduled(fixedDelay = 60000)
    public void retryUndeliveredMessages() {
        try {
            alertCommandService.handle(new RetryUndeliveredAlertsCommand(5));
        } catch (Exception e) {
            log.warn("Error running scheduled alert retries: {}", e.getMessage());
        }

        try {
            notificationCommandService.handle(new RetryUndeliveredPushNotificationsCommand(5));
        } catch (Exception e) {
            log.warn("Error running scheduled push notification retries: {}", e.getMessage());
        }
    }
}
