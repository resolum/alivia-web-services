package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.commands.CreatePreauthorizationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PreauthorizationResult;

public interface PreauthorizationCommandService {
    PreauthorizationResult handle(CreatePreauthorizationCommand command);
}
