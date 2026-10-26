package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfigurationDraftService {
    private final ConfigurationDraftRepository repository;

    public ConfigurationDraftService(ConfigurationDraftRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConfigurationDraft create(PlanCode planCode, String billingCycle) {
        return repository.save(new ConfigurationDraft(planCode, billingCycle));
    }

    @Transactional(readOnly = true)
    public ConfigurationDraft find(String id) {
        return repository.findById(id).orElseThrow(() -> new ConfigurationDraftNotFound(id));
    }

    @Transactional
    public ConfigurationDraft replaceRooms(String id, List<ConfiguredRoom> rooms) {
        var draft = find(id);
        draft.replaceRooms(rooms);
        return repository.save(draft);
    }
}
