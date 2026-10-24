package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.acl.IamContextFacadeImpl;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IamContextFacadeImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final IamContextFacadeImpl facade = new IamContextFacadeImpl(userRepository);

    @Test
    void returnsTrueWhenTheEmailIsRegistered() {
        when(userRepository.existsByEmail(new EmailAddress("maria.lopez@example.com")))
                .thenReturn(true);

        assertTrue(facade.existsUserByEmail("  Maria.Lopez@Example.COM "));

        verify(userRepository).existsByEmail(new EmailAddress("maria.lopez@example.com"));
    }

    @Test
    void returnsFalseWhenTheEmailIsNotRegistered() {
        when(userRepository.existsByEmail(new EmailAddress("maria.lopez@example.com")))
                .thenReturn(false);

        assertFalse(facade.existsUserByEmail("maria.lopez@example.com"));

        verify(userRepository).existsByEmail(new EmailAddress("maria.lopez@example.com"));
    }

    @Test
    void returnsFalseWithoutQueryingPersistenceForBlankEmail() {
        assertFalse(facade.existsUserByEmail(null));
        assertFalse(facade.existsUserByEmail("   "));

        verifyNoInteractions(userRepository);
    }

    @Test
    void doesNotQueryPersistenceWhenTheEmailIsInvalid() {
        assertFalse(facade.existsUserByEmail("not-an-email"));

        verifyNoInteractions(userRepository);
    }
}
