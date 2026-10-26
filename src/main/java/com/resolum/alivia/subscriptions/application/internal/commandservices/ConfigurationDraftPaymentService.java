package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.application.outboundservices.StripeService;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConfigurationDraftPaymentService {
    private final ConfigurationDraftRepository drafts;
    private final PlanQueryService plans;
    private final StripeService stripe;
    private final PaymentAuthorizationGateway authorizations;

    public ConfigurationDraftPaymentService(ConfigurationDraftRepository drafts, PlanQueryService plans,
                                            StripeService stripe, PaymentAuthorizationGateway authorizations) {
        this.drafts = drafts;
        this.plans = plans;
        this.stripe = stripe;
        this.authorizations = authorizations;
    }

    @Transactional
    public PaymentSetup prepare(String draftId, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new IllegalArgumentException("La clave de idempotencia es obligatoria.");
        var draft = find(draftId);
        var customerId = draft.getStripeCustomerId();
        if (customerId == null) {
            customerId = stripe.createCheckoutCustomer(draft.getId());
            draft.linkStripeCustomer(customerId);
        }
        var setup = stripe.createPaymentSetup(customerId, idempotencyKey);
        draft.recordPaymentSetup(setup.setupIntentId());
        drafts.saveAndFlush(draft);
        return setup;
    }

    @Transactional
    public StripeAuthorization preauthorize(String draftId, String paymentMethodId, UUID idempotencyKey) {
        if (idempotencyKey == null || paymentMethodId == null || !paymentMethodId.startsWith("pm_")) {
            throw new IllegalArgumentException("Se requieren Idempotency-Key y PaymentMethod de Stripe.");
        }
        var draft = find(draftId);
        if (draft.getStripePaymentIntentId() != null) {
            var current = authorizations.retrieve(new StripePaymentIntentId(draft.getStripePaymentIntentId()));
            draft.recordPreauthorization(current);
            drafts.save(draft);
            return current;
        }
        if (draft.getStripeCustomerId() == null || draft.getStripeSetupIntentId() == null) {
            throw new IllegalStateException("Primero registra el medio de pago del borrador.");
        }
        var plan = plans.handle(new GetPlanByCodeQuery(draft.getPlanCode()));
        var quote = plan.quoteMonthly(draft.getRooms().size(), draft.actuatorQuantities().toSelection());
        var cents = quote.totalMonthlyAmount().movePointRight(2).longValueExact();
        var authorization = authorizations.create(draft.getStripeCustomerId(), paymentMethodId, cents,
                quote.currency(), idempotencyKey);
        try {
            draft.recordPreauthorization(authorization);
            drafts.saveAndFlush(draft);
            return authorization;
        } catch (RuntimeException persistenceFailure) {
            try {
                authorizations.cancel(authorization.intentId());
            } catch (RuntimeException cancellationFailure) {
                persistenceFailure.addSuppressed(cancellationFailure);
            }
            throw persistenceFailure;
        }
    }

    @Transactional
    public StripeAuthorization status(String draftId) {
        var draft = find(draftId);
        if (draft.getStripePaymentIntentId() == null) {
            throw new IllegalStateException("El borrador todavía no tiene una preautorización.");
        }
        var current = authorizations.retrieve(new StripePaymentIntentId(draft.getStripePaymentIntentId()));
        draft.recordPreauthorization(current);
        drafts.save(draft);
        return current;
    }

    private ConfigurationDraft find(String id) {
        return drafts.findById(id).orElseThrow(() -> new ConfigurationDraftNotFound(id));
    }
}
