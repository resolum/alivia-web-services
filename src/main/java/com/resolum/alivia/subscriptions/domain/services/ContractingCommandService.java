package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.commands.ClaimConfigurationDraftCommand;
import com.resolum.alivia.subscriptions.domain.model.commands.ConfirmConfigurationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.MyContracting;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;

public interface ContractingCommandService {
    ContractingTracking handle(ConfirmConfigurationCommand command);

    /** Links the request to the account and answers its stage. */
    MyContracting handle(ClaimConfigurationDraftCommand command);
}
