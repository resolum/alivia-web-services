package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.commands.SeedPlansCommand;

public interface PlanCommandService {
    void handle(SeedPlansCommand command);
}
