package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationNotReadyToConfirm;
import com.resolum.alivia.subscriptions.domain.model.commands.ConfirmConfigurationCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetContractingTrackingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;
import com.resolum.alivia.subscriptions.domain.services.ContractingCommandService;
import com.resolum.alivia.subscriptions.domain.services.ContractingTrackingQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class ContractingCommandServiceImpl implements ContractingCommandService {
    private final ConfigurationDraftRepository drafts;
    private final ContractingTrackingQueryService tracking;
    private final Clock clock;

    public ContractingCommandServiceImpl(ConfigurationDraftRepository drafts, ContractingTrackingQueryService tracking,
                                         Clock clock) {
        this.drafts = drafts;
        this.tracking = tracking;
        this.clock = clock;
    }

    /**
     * Records that the family reviewed and confirmed the configuration the technician validated, which moves
     * the request to the installation. Confirming an already confirmed request answers its current tracking.
     */
    @Override
    @Transactional
    public ContractingTracking handle(ConfirmConfigurationCommand command) {
        var draft = drafts.findById(command.draftId()).orElseThrow(() -> new ConfigurationDraftNotFound(command.draftId()));
        var current = tracking.handle(new GetContractingTrackingQuery(draft.getId()));
        if (draft.isConfirmed()) return current;
        if (current.stage() != ContractingStage.CONFIRMATION) throw new ConfigurationNotReadyToConfirm();
        draft.confirm(Instant.now(clock));
        drafts.save(draft);
        return tracking.handle(new GetContractingTrackingQuery(draft.getId()));
    }
}
