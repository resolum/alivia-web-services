package com.resolum.alivia.subscriptions.domain.model.queries;

/**
 * Homes in service. With {@code technicianUserId}, only those the technician has served.
 */
public record GetServicedHomesQuery(Long technicianUserId) {
}
