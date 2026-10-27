package com.resolum.alivia.iam.infrastructure.adapters;

import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories.UserPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link UserRepository} that uses PostgreSQL for data storage.
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserPersistenceRepository persistenceRepository;

    /**
     * Creates the adapter.
     *
     * @param persistenceRepository the Spring Data repository
     */
    public UserRepositoryImpl(UserPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByEmail(EmailAddress email) {
        return persistenceRepository.existsByEmail(email.email());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findById(Long id) {
        return persistenceRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return persistenceRepository.findByEmail(email.email());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findByEmailVerificationTokenHash(String tokenHash) {
        return persistenceRepository.findByEmailVerificationTokenHash(tokenHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findByRefreshTokenHash(String tokenHash) {
        return persistenceRepository.findByRefreshTokenHash(tokenHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findByAccountId(String accountId) {
        return persistenceRepository.findByAccountId(accountId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<User> findAllByIds(Collection<Long> ids) {
        return persistenceRepository.findAllById(ids);
    }

    /**
     * {@inheritDoc}
     *
     * @throws EmailAlreadyRegisteredException when the email unique constraint is violated
     */
    @Override
    public User save(User user) {
        try {
            return persistenceRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
