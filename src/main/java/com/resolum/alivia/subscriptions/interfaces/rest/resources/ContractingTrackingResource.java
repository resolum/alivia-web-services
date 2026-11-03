package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tracking of a contracting request for the family.
 *
 * @param stage {@code EVALUATION}, {@code CONFIRMATION}, {@code INSTALLATION}, {@code ACTIVATION} or
 *              {@code NOT_VIABLE}
 */
public record ContractingTrackingResource(String draftId, String code, String stage, String planCode, String planName,
                                          int rooms, int devices, BigDecimal monthlyAmount, String currency,
                                          TechnicalVisitResource evaluation, TechnicalVisitResource installation,
                                          Instant confirmedAt) {
}
