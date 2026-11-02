package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.eventhandlers.SuperAdminInitializer;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAdminInitializerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final HashingService hashingService = mock(HashingService.class);
    private final SuperAdminInitializer initializer = new SuperAdminInitializer(
            userRepository, hashingService, Clock.systemUTC(), "superadmin@alivia.com", "Alivia#Admin2026");

    @Test
    void createsActiveVerifiedAdministratorWhenMissing() {
        when(hashingService.encode("Alivia#Admin2026")).thenReturn("hash");

        initializer.initialize();

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        var user = captor.getValue();
        assertEquals(AccessRole.ADMINISTRATOR, user.getAccessRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.getEmailVerified());
    }

    @Test
    void skipsWhenEmailAlreadyRegistered() {
        when(userRepository.existsByEmail(any())).thenReturn(true);

        initializer.initialize();

        verify(userRepository, never()).save(any());
    }
}
