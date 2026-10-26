package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ExpiredDraftPreauthorizationReleaser {
    private final ConfigurationDraftRepository drafts;
    private final PaymentAuthorizationGateway authorizations;

    public ExpiredDraftPreauthorizationReleaser(ConfigurationDraftRepository drafts,
                                                PaymentAuthorizationGateway authorizations) {
        this.drafts = drafts;
        this.authorizations = authorizations;
    }

    @Scheduled(fixedDelayString = "${payments.preauthorization.release-check-ms:3600000}")
    @Transactional
    public void releaseExpired() {
        var now = Instant.now();
        for (var draft : drafts.findByPaymentStatus(PaymentStatus.PREAUTHORIZED)) {
            if (!draft.preauthorizationReleaseDue(now)) continue;
            authorizations.cancel(new StripePaymentIntentId(draft.getStripePaymentIntentId()));
            draft.markPreauthorizationCanceled();
            drafts.save(draft);
        }
    }
}
