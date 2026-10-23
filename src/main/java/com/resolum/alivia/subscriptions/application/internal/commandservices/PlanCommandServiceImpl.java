package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.commands.SeedPlansCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.services.PlanCommandService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PlanCommandServiceImpl implements PlanCommandService {
    private final PlanRepository repository;

    public PlanCommandServiceImpl(PlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @Override
    public void handle(SeedPlansCommand command) {
        if (!repository.existsByCode(PlanCode.MINIMUM)) {
            repository.save(PlanCatalogDefaults.base());
        }
        if (!repository.existsByCode(PlanCode.CUSTOM)) {
            repository.save(PlanCatalogDefaults.custom());
        }
    }
}
