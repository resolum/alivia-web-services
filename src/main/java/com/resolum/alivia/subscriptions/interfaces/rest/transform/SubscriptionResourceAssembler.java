package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Payment;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.SubscriptionResource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SubscriptionResourceAssembler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static SubscriptionResource toResource(Subscription s, Payment p) {
        return toResource(s, p, null, null);
    }

    public static SubscriptionResource toResource(Subscription s, Payment p, String holderName, Integer roomsCount) {
        if (s == null) return null;

        String statusStr = switch (s.getStatus()) {
            case ACTIVE -> "active";
            case PAST_DUE -> "suspended";
            case CANCELED -> "cancelled";
            case PENDING_PAYMENT -> "active";
        };

        String planCode = s.getPlan() != null ? s.getPlan().getCode().name() : "MINIMUM";
        String planName = s.getPlan() != null && s.getPlan().getName() != null && !s.getPlan().getName().isBlank()
                ? s.getPlan().getName()
                : ("MINIMUM".equals(planCode) ? "Plan Base" : "Plan Personalizado");

        String currency = s.getCurrency() == null || "PEN".equalsIgnoreCase(s.getCurrency())
                ? "S/"
                : s.getCurrency();

        String startedOn = s.getCurrentPeriod() != null && s.getCurrentPeriod().start() != null
                ? s.getCurrentPeriod().start().format(DATE_FORMAT)
                : LocalDate.now().format(DATE_FORMAT);
        String renewsOn = s.getCurrentPeriod() != null && s.getCurrentPeriod().end() != null
                ? s.getCurrentPeriod().end().format(DATE_FORMAT)
                : LocalDate.now().plusMonths(1).format(DATE_FORMAT);

        int totalDevices = s.getActuators() != null && !s.getActuators().isEmpty()
                ? s.getActuators().values().stream().mapToInt(Integer::intValue).sum()
                : (s.getPlan() != null ? s.getPlan().getMinActuators() : 0);
        int resolvedRooms = roomsCount != null && roomsCount > 0
                ? roomsCount
                : (s.getPlan() != null && s.getPlan().getMinimumRooms() > 0 ? s.getPlan().getMinimumRooms() : 1);

        String resolvedHolder = holderName != null && !holderName.isBlank()
                ? holderName
                : "Titular de la cuenta";
        String brand = s.getStripeCustomerId() != null && !s.getStripeCustomerId().isBlank()
                ? "Stripe"
                : "Tarjeta";
        boolean autoCharge = s.getStripeSubscriptionId() != null && !s.getStripeSubscriptionId().isBlank();

        var paymentMethod = new SubscriptionResource.PaymentMethodResource(
                brand,
                "••••",
                resolvedHolder,
                autoCharge
        );

        SubscriptionResource.SubscriptionPaymentResource lastPayment = null;
        if (p != null) {
            String paidAt = p.getPeriod() != null && p.getPeriod().start() != null
                    ? p.getPeriod().start().format(DATE_FORMAT)
                    : startedOn;
            lastPayment = new SubscriptionResource.SubscriptionPaymentResource(
                    "pay-" + p.getId(),
                    p.getAmount(),
                    paidAt,
                    p.isSucceeded() ? "paid" : "failed",
                    p.getReceiptUrl()
            );
        }

        return new SubscriptionResource(
                String.valueOf(s.getId()),
                statusStr,
                planCode,
                planName,
                currency,
                s.getMonthlyAmount(),
                startedOn,
                renewsOn,
                resolvedRooms,
                totalDevices,
                paymentMethod,
                lastPayment,
                s.getPendingPlanCode()
        );
    }
}
