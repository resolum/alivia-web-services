package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.services.UserDetailsServiceImpl;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository);

    @Test
    void loadsUserDetailsById() {
        var user = new User(new EmailAddress("maria.lopez@example.com"), new PasswordHash("bcrypt-hash"),
                AccessRole.CAREGIVER, LocalDateTime.parse("2026-10-24T12:00:00"));
        ReflectionTestUtils.setField(user, "id", 42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername("42");

        assertEquals("42", details.getUsername());
        assertEquals("bcrypt-hash", details.getPassword());
        assertEquals(List.of("ROLE_CAREGIVER"),
                details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        assertTrue(details.isEnabled());
        ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
        assertTrue(details.isEnabled());
        ReflectionTestUtils.setField(user, "status", UserStatus.BLOCKED);
        assertFalse(details.isEnabled());
    }

    @Test
    void rejectsUnknownOrMalformedIds() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("7"));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("abc"));
    }
}
