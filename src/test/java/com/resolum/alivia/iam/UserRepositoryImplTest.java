package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.infrastructure.adapters.UserRepositoryImpl;
import com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories.UserPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryImplTest {

    private final UserPersistenceRepository persistenceRepository = mock(UserPersistenceRepository.class);
    private final UserRepositoryImpl repository = new UserRepositoryImpl(persistenceRepository);

    @Test
    void delegatesQueriesToPersistenceRepository() {
        var email = new EmailAddress("user@example.com");
        var user = mock(User.class);

        when(persistenceRepository.existsByEmail("user@example.com")).thenReturn(true);
        when(persistenceRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(persistenceRepository.findByEmailVerificationTokenHash("hash123")).thenReturn(Optional.of(user));

        assertTrue(repository.existsByEmail(email));
        assertEquals(Optional.of(user), repository.findByEmail(email));
        assertEquals(Optional.of(user), repository.findByEmailVerificationTokenHash("hash123"));

        verify(persistenceRepository).existsByEmail("user@example.com");
        verify(persistenceRepository).findByEmail("user@example.com");
        verify(persistenceRepository).findByEmailVerificationTokenHash("hash123");
    }

    @Test
    void savesUserSuccessfully() {
        var user = mock(User.class);
        when(persistenceRepository.saveAndFlush(user)).thenReturn(user);

        User saved = repository.save(user);

        assertEquals(user, saved);
        verify(persistenceRepository).saveAndFlush(user);
    }

    @Test
    void translatesDataIntegrityViolationException() {
        var user = mock(User.class);
        when(persistenceRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(EmailAlreadyRegisteredException.class, () -> repository.save(user));
    }
}
