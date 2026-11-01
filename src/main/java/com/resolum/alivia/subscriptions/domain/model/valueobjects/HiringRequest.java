package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A family's contracting request as the subscription managers see it in the hiring pipeline.
 *
 * @param heldAmount monthly amount held on the card, or null while the configuration is incomplete
 */
public record HiringRequest(String draftId, PlanCode planCode, String planName, String billingCycle, int rooms,
                            int devices, HiringStage stage, PaymentStatus paymentStatus, BigDecimal heldAmount,
                            String currency, Instant preauthorizedAt, Instant captureBefore, Instant updatedAt) {
}
