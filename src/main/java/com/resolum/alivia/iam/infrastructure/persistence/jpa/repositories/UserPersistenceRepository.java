package com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * Spring Data repository for {@link User} aggregates.
 */
public interface UserPersistenceRepository extends JpaRepository<User, Long> {

    /**
     * Checks whether a user exists with the given email address.
     *
     * @param email the normalized email address
     * @return {@code true} when the email is registered
     */
    boolean existsByEmail(String email);

    /**
     * Finds a user by email address.
     *
     * @param email the normalized email address
     * @return the matching user, or empty when none exists
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by identifier and locks the row for writing.
     *
     * @param accountId the user identifier
     * @return the matching user, or empty when none exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long accountId);
}
