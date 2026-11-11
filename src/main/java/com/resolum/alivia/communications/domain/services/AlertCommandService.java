package com.resolum.alivia.communications.domain.services;

import com.resolum.alivia.communications.domain.model.commands.CreateAlertCommand;
import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredAlertsCommand;
import com.resolum.alivia.communications.domain.model.commands.SendAlertCommand;

public interface AlertCommandService {
    Long handle(CreateAlertCommand command);
    void handle(SendAlertCommand command);
    void handle(RetryUndeliveredAlertsCommand command);
}
