package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetupStatus;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PaymentSetupStatusResource;

public final class PaymentSetupStatusResourceFromResultsAssembler {
    private PaymentSetupStatusResourceFromResultsAssembler() {
    }

    public static PaymentSetupStatusResource toResourceFromResult(PaymentSetupStatus result) {
        return new PaymentSetupStatusResource(result.setupIntentId(), result.status(),
                "succeeded".equals(result.status()) && result.paymentMethodId() != null);
    }
}
