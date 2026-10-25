package com.resolum.alivia.iam.domain.model.queries;

/**
 * Query that looks up a user by its public account identifier.
 *
 * @param accountId the public account identifier
 */
public record GetUserByAccountIdQuery(String accountId) {

    /**
     * Creates the query.
     *
     * @param accountId the public account identifier
     * @throws IllegalArgumentException if the account identifier is null or blank
     */
    public GetUserByAccountIdQuery {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account id is required.");
        }
    }
}
