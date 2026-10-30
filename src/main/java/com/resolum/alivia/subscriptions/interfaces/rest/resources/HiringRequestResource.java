package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;

public record HiringRequestResource(String id, String code, String planCode, String planName, String billingCycle,
                                    int rooms, int devices, String stage, String paymentStatus, BigDecimal heldAmount,
                                    String currency, Instant preauthorizedAt, Instant captureBefore, Instant updatedAt) {
}
