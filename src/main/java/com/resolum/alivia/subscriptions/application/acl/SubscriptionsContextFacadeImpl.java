package com.resolum.alivia.subscriptions.application.acl;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionsContextFacadeImpl implements SubscriptionsContextFacade {
    private final ConfigurationDraftRepository drafts;

    public SubscriptionsContextFacadeImpl(ConfigurationDraftRepository drafts) {
        this.drafts = drafts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConfigurationDraftPreauthorized(String draftId) {
        if (draftId == null || draftId.isBlank()) return false;
        return drafts.findById(draftId)
                .map(draft -> draft.getPaymentStatus() == PaymentStatus.PREAUTHORIZED)
                .orElse(false);
    }
}
