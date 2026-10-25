package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.acl.IamContextFacadeImpl;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IamContextFacadeImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserCommandService userCommandService = mock(UserCommandService.class);
    private final IamContextFacadeImpl facade = new IamContextFacadeImpl(userRepository, userCommandService);

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

    @Test
    void signUpUserDelegatesToUserCommandService() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(101L);
        when(userCommandService.handle(any(SignUpCommand.class))).thenReturn(user);

        Long id = facade.signUpUser("emp@example.com", "Password1!", "Emp Name", "+51999999999",
                "EMPLOYEE", 44L, null);

        assertEquals(101L, id);
        verify(userCommandService).handle(any(SignUpCommand.class));
    }

    @Test
    void registerCompanyUserDelegatesToSignUpUser() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(102L);
        when(userCommandService.handle(any(SignUpCommand.class))).thenReturn(user);

        Long id = facade.registerCompanyUser("emp@example.com", "Password1!", "Emp Name", "+51999999999",
                "TECHNICIAN", 55L);

        assertEquals(102L, id);
        verify(userCommandService).handle(any(SignUpCommand.class));
    }

    @Test
    void registerAssistedPersonDelegatesToSignUpUser() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(103L);
        when(userCommandService.handle(any(SignUpCommand.class))).thenReturn(user);

        Long id = facade.registerAssistedPerson("assisted@example.com", "Password1!", "Assisted Name",
                "+51999999999", 77L);

        assertEquals(103L, id);
        verify(userCommandService).handle(any(SignUpCommand.class));
    }
}
