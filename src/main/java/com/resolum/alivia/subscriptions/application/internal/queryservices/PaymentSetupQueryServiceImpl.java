package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.application.outboundservices.StripeService;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPaymentSetupStatusQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetupStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.resolum.alivia.subscriptions.domain.services.PaymentSetupQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.UserStripeCustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentSetupQueryServiceImpl implements PaymentSetupQueryService {
    private final StripeService stripeService;
    private final UserStripeCustomerRepository customers;

    public PaymentSetupQueryServiceImpl(StripeService stripeService, UserStripeCustomerRepository customers) {
        this.stripeService = stripeService;
        this.customers = customers;
    }

    @Override
    public PaymentSetupStatus handle(GetPaymentSetupStatusQuery query) {
        String identity = query.authenticatedUserId();
        UserId userId;
        try {
            userId = new UserId(Long.valueOf(identity));
        } catch (IllegalArgumentException exception) {
            throw new PaymentSetupUnauthorized();
        }
        var customerId = customers.findById(userId.value())
                .orElseThrow(PaymentSetupNotFound::new).getStripeCustomerId();
        var setup = stripeService.retrievePaymentSetup(query.setupIntentId());
        if (!customerId.equals(setup.customerId())) {
            throw new PaymentSetupNotFound();
        }
        return setup;
    }
}
