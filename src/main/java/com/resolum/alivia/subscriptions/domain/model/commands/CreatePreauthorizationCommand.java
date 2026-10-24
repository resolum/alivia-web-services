package com.resolum.alivia.subscriptions.domain.model.commands;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantity;
import java.util.List;
import java.util.UUID;

public record CreatePreauthorizationCommand(String authenticatedUserId, String householdId, Long planId,
                                            int rooms, List<ActuatorQuantity> actuators, String paymentMethodId,
                                            UUID idempotencyKey) { }
