package com.resolum.alivia.subscriptions.application.internal.eventhandlers;

import com.resolum.alivia.subscriptions.domain.model.commands.SeedPlansCommand;
import com.resolum.alivia.subscriptions.domain.services.PlanCommandService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PlanCatalogInitializer {
    private final PlanCommandService commandService;

    public PlanCatalogInitializer(PlanCommandService commandService) {
        this.commandService = commandService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        commandService.handle(new SeedPlansCommand());
    }
}
