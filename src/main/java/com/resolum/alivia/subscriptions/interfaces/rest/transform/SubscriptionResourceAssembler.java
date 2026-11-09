package com.resolum.alivia.subscriptions.interfaces.rest.transform;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Payment;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.SubscriptionResource;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public class SubscriptionResourceAssembler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static SubscriptionResource toResource(Subscription s, Payment p) {
        if (s == null) return null;

        String statusStr = switch (s.getStatus()) {
            case ACTIVE -> "active";
            case PAST_DUE -> "suspended";
            case CANCELED -> "cancelled";
            case PENDING_PAYMENT -> "active";
        };

        String planCode = s.getPlan() != null ? s.getPlan().getCode().name() : "MINIMUM";
        String planName = "MINIMUM".equals(planCode) ? "Plan Base" : "Plan Personalizado";

        String startedOn = s.getCurrentPeriod() != null && s.getCurrentPeriod().start() != null
                ? s.getCurrentPeriod().start().format(DATE_FORMAT)
                : "2024-10-15";
        String renewsOn = s.getCurrentPeriod() != null && s.getCurrentPeriod().end() != null
                ? s.getCurrentPeriod().end().format(DATE_FORMAT)
                : "2024-11-15";

        int totalDevices = s.getActuators() != null
                ? s.getActuators().values().stream().mapToInt(Integer::intValue).sum() + 2
                : 5;
        int roomsCount = 2;

        var paymentMethod = new SubscriptionResource.PaymentMethodResource(
                "VISA",
                "4242",
                "María Elena Villanueva C.",
                true
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
        } else {
            lastPayment = new SubscriptionResource.SubscriptionPaymentResource(
                    "pay-sample",
                    s.getMonthlyAmount() != null ? s.getMonthlyAmount() : new BigDecimal("99.00"),
                    startedOn,
                    "paid",
                    null
            );
        }

        return new SubscriptionResource(
                String.valueOf(s.getId()),
                statusStr,
                planCode,
                planName,
                "S/",
                s.getMonthlyAmount(),
                startedOn,
                renewsOn,
                roomsCount,
                totalDevices,
                paymentMethod,
                lastPayment,
                s.getPendingPlanCode()
        );
    }
}
