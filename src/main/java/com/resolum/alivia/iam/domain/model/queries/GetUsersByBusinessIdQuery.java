package com.resolum.alivia.iam.domain.model.queries;

/**
 * Query that lists the accounts of the employees of a business, ordered by id.
 *
 * @param businessId the business; {@code null} means the default business
 */
public record GetUsersByBusinessIdQuery(Long businessId) {
}
