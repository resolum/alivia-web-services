package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.queries.GetPaymentSetupStatusQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetupStatus;

public interface PaymentSetupQueryService {
    PaymentSetupStatus handle(GetPaymentSetupStatusQuery query);
}
