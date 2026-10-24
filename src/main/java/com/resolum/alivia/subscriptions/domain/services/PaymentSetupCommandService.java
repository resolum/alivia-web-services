package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.commands.PreparePaymentSetupCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;

public interface PaymentSetupCommandService {
    PaymentSetup handle(PreparePaymentSetupCommand command);
}
