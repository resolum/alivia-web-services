package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.commands.ConfirmConfigurationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;

public interface ContractingCommandService {
    ContractingTracking handle(ConfirmConfigurationCommand command);
}
