package com.resolum.alivia.operations.domain.model.queries;

/** Orders assigned to the technician signed in as {@code userId}. */
public record GetTechnicianOrdersQuery(Long userId) {
}
