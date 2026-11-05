package com.resolum.alivia.subscriptions.domain.model.queries;

/**
 * One home in service, by its contracting request. With {@code technicianUserId}, only when that technician
 * has served it.
 */
public record GetServicedHomeByIdQuery(String draftId, Long technicianUserId) {
}
