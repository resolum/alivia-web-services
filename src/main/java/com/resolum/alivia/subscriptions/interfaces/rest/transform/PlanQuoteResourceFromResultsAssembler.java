package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PlanQuoteResource;

public final class PlanQuoteResourceFromResultsAssembler {
    private PlanQuoteResourceFromResultsAssembler() {
    }

    public static PlanQuoteResource toResourceFromResult(PlanQuote quote) {
        return new PlanQuoteResource(quote.planCode(), quote.currency(), "MONTHLY", quote.rooms(),
                quote.deviceQuantities(), quote.baseMonthlyAmount(),
                quote.additionalRoomsMonthlyAmount(), quote.devicesMonthlyAmount(), quote.totalMonthlyAmount());
    }
}
