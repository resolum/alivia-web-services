package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PaymentSetupResource;

public final class PaymentSetupResourceFromResultsAssembler {
    private PaymentSetupResourceFromResultsAssembler() {
    }

    public static PaymentSetupResource toResourceFromResult(PaymentSetup result) {
        return new PaymentSetupResource(result.setupIntentId(), result.clientSecret(), result.publishableKey());
    }
}
