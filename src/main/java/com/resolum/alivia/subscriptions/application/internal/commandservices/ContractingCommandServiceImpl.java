package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftOwnedByAnotherAccount;
import com.resolum.alivia.subscriptions.domain.model.commands.ClaimConfigurationDraftCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetMyContractingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.MyContracting;
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
    private final ExternalOperationsService operations;
    private final Clock clock;

    public ContractingCommandServiceImpl(ConfigurationDraftRepository drafts, ContractingTrackingQueryService tracking,
                                         ExternalOperationsService operations, Clock clock) {
        this.drafts = drafts;
        this.tracking = tracking;
        this.operations = operations;
        this.clock = clock;
    }

    /**
     * Records that the family reviewed and confirmed the configuration the technician validated, which moves
     * the request to the installation: the technician who evaluated the home gets the installation order.
     * Confirming an already confirmed request opens that order if it is missing and answers its tracking.
     */
    @Override
    @Transactional
    public ContractingTracking handle(ConfirmConfigurationCommand command) {
        var draft = drafts.findById(command.draftId()).orElseThrow(() -> new ConfigurationDraftNotFound(command.draftId()));
        if (!draft.isConfirmed()) {
            var current = tracking.handle(new GetContractingTrackingQuery(draft.getId()));
            if (current.stage() != ContractingStage.CONFIRMATION) throw new ConfigurationNotReadyToConfirm();
            draft.confirm(Instant.now(clock));
            drafts.save(draft);
        }
        operations.openInstallation(draft.getId());
        return tracking.handle(new GetContractingTrackingQuery(draft.getId()));
    }

    @Override
    @Transactional
    public MyContracting handle(ClaimConfigurationDraftCommand command) {
        var draft = drafts.findById(command.draftId()).orElseThrow(() -> new ConfigurationDraftNotFound(command.draftId()));
        if (!draft.claimBy(command.userId())) throw new ConfigurationDraftOwnedByAnotherAccount();
        drafts.save(draft);
        return tracking.handle(new GetMyContractingQuery(command.userId()))
                .filter(mine -> mine.draftId().equals(draft.getId()))
                .orElseGet(() -> new MyContracting(draft.getId(), MyContracting.CONFIGURATION, draft.getPlanCode().name(),
                        draft.getBillingCycle(), false));
    }
}
