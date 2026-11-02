package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.queries.GetUserByAccountIdQuery;

import java.util.Optional;

/**
 * Query service of the IAM bounded context.
 */
public interface UserQueryService {

    /**
     * Finds a user by its public account identifier.
     *
     * @param query the get-user-by-account-id query
     * @return the matching user, or empty when none exists
     */
    Optional<User> handle(GetUserByAccountIdQuery query);
}
