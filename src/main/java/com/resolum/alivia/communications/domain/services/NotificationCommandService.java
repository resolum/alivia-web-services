package com.resolum.alivia.communications.domain.services;

import com.resolum.alivia.communications.domain.model.commands.CreateNotificationCommand;
import com.resolum.alivia.communications.domain.model.commands.MarkNotificationAsReadCommand;
import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredPushNotificationsCommand;
import com.resolum.alivia.communications.domain.model.commands.SendPushNotificationCommand;

public interface NotificationCommandService {
    Long handle(CreateNotificationCommand command);
    void handle(SendPushNotificationCommand command);
    void handle(RetryUndeliveredPushNotificationsCommand command);
    Long handle(MarkNotificationAsReadCommand command);
}
