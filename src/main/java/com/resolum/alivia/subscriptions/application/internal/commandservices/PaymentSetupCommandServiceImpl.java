package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.domain.model.commands.PreparePaymentSetupCommand;
import com.resolum.alivia.subscriptions.application.outboundservices.StripeService;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.model.entities.UserStripeCustomer;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.resolum.alivia.subscriptions.domain.services.PaymentSetupCommandService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.UserStripeCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSetupCommandServiceImpl implements PaymentSetupCommandService {
    private final StripeService stripeService;
    private final UserStripeCustomerRepository customers;

    public PaymentSetupCommandServiceImpl(StripeService stripeService, UserStripeCustomerRepository customers) {
        this.stripeService = stripeService;
        this.customers = customers;
    }

    @Override
    @Transactional
    public PaymentSetup handle(PreparePaymentSetupCommand command) {
        var userId = resolveUserId(command.authenticatedUserId());
        var customerId = customers.findById(userId.value())
                .map(UserStripeCustomer::getStripeCustomerId)
                .orElseGet(() -> {
                    var stripeCustomerId = stripeService.createCustomer(userId);
                    customers.saveAndFlush(new UserStripeCustomer(userId, stripeCustomerId));
                    return stripeCustomerId;
                });
        return stripeService.createPaymentSetup(customerId, command.idempotencyKey());
    }

    private UserId resolveUserId(String authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new PaymentSetupUnauthorized();
        }
        try {
            return new UserId(Long.valueOf(authenticatedUserId));
        } catch (IllegalArgumentException exception) {
            throw new PaymentSetupUnauthorized();
        }
    }
}
