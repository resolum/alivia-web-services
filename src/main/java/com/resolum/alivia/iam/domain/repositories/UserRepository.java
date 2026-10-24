package com.resolum.alivia.iam.domain.repositories;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

import java.util.Optional;

/**
 * Persistence port for {@link User} aggregates.
 */
public interface UserRepository {

    /**
     * Checks whether a user with the given email address exists.
     *
     * @param email the email address
     * @return {@code true} when a user is registered with that email
     */
    boolean existsByEmail(EmailAddress email);

    /**
     * Finds a user by email address.
     *
     * @param email the email address
     * @return the matching user, or empty when none exists
     */
    Optional<User> findByEmail(EmailAddress email);

    /**
     * Finds a user by identifier and locks the row for writing.
     *
     * <p>The lock serializes concurrent email verifications for the same account.</p>
     *
     * @param accountId the user identifier
     * @return the matching user, or empty when none exists
     */
    Optional<User> findByIdForUpdate(Long accountId);

    /**
     * Persists a user.
     *
     * @param user the user to persist
     * @return the persisted user with its generated identifier
     */
    User save(User user);
}
