package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Where a family's contracting request stands after the payment, with the visits that move it.
 *
 * @param monthlyAmount monthly amount of the configuration, or null while it breaks the plan limits
 * @param evaluation    the evaluation visit, or null while none is booked
 * @param installation  the installation visit, or null while none is booked
 * @param confirmedAt   when the family confirmed the configuration the technician validated
 */
public record ContractingTracking(String draftId, ContractingStage stage, PlanCode planCode, String planName,
                                  int rooms, int devices, BigDecimal monthlyAmount, String currency,
                                  TechnicalVisit evaluation, TechnicalVisit installation, Instant confirmedAt) {
}
