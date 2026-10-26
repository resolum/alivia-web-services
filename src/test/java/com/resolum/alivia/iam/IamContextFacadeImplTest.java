package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.acl.IamContextFacadeImpl;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void registerUserDelegatesToSignUpUser() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(104L);
        when(userCommandService.handle(any(SignUpCommand.class))).thenReturn(user);

        Long id = facade.registerUser("user@example.com", "Password1!", "Name", "+51999999999", "ADMIN");

        assertEquals(104L, id);
    }

    @Test
    void parsesDifferentRoleFormats() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(105L);
        when(userCommandService.handle(any(SignUpCommand.class))).thenReturn(user);

        facade.signUpUser("u@e.com", "P!", "N", "+51", null, null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "   ", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "ADMINISTRADOR", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "CUIDADOR", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "TECNICO", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "GESTOR_SUSCRIPCIONES", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "PERSONA_ASISTIDA", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "CAREGIVER", null, null);
        facade.signUpUser("u@e.com", "P!", "N", "+51", "INVALID_ROLE_FALLBACK", null, null);

        assertTrue(true);
    }

    @Test
    void fetchesEmailsByUserIdsInOneBatch() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn(new EmailAddress("ana.torres@example.com"));
        when(userRepository.findAllByIds(Set.of(7L, 99L))).thenReturn(List.of(user));

        assertEquals(Map.of(7L, "ana.torres@example.com"), facade.fetchEmailsByUserIds(Set.of(7L, 99L)));
    }

    @Test
    void fetchEmailsSkipsPersistenceForNoUsers() {
        assertEquals(Map.of(), facade.fetchEmailsByUserIds(Set.of()));
        assertEquals(Map.of(), facade.fetchEmailsByUserIds(null));

        verifyNoInteractions(userRepository);
    }
}
