package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-24T12:00:00");

    @Test
    void newUserStartsPendingVerification() {
        var user = newUser();

        assertEquals("maria.lopez@example.com", user.getEmail().email());
        assertEquals("bcrypt-hash", user.getPassword().value());
        assertEquals(AccessRole.CAREGIVER, user.getAccessRole());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
        assertFalse(user.getEmailVerified());
        assertEquals(Integer.valueOf(0), user.getFailedAccessAttempts());
        assertEquals(NOW, user.getTermsAcceptedAt());
        assertNull(user.getPendingEmailVerificationToken());
    }

    @Test
    void requestEmailVerificationReplacesPendingToken() {
        var user = newUser();
        var first = new EmailVerificationToken("hash-1", NOW.plusHours(1));
        var second = new EmailVerificationToken("hash-2", NOW.plusHours(2));

        user.requestEmailVerification(first);
        user.requestEmailVerification(second);

        assertNotNull(user.getPendingEmailVerificationToken());
        assertEquals("hash-2", user.getPendingEmailVerificationToken().value());
        assertEquals(NOW.plusHours(2), user.getPendingEmailVerificationToken().expiresAt());
    }

    @Test
    void verifyEmailActivatesAccountAndConsumesToken() {
        var user = newUser();
        user.requestEmailVerification(new EmailVerificationToken("hash", NOW.plusHours(1)));

        user.verifyEmail();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.getEmailVerified());
        assertNull(user.getPendingEmailVerificationToken());
    }

    @Test
    void verifyEmailIsRejectedWhenAccountIsNoLongerPending() {
        var user = newUser();
        user.verifyEmail();

        assertThrows(AccountNotPendingVerificationException.class, user::verifyEmail);
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void verifyEmailIsRejectedForBlockedAccountWithPendingToken() {
        var user = newUser();
        user.requestEmailVerification(new EmailVerificationToken("hash", NOW.plusHours(1)));
        ReflectionTestUtils.setField(user, "status", UserStatus.BLOCKED);

        assertThrows(AccountNotPendingVerificationException.class, user::verifyEmail);

        assertEquals(UserStatus.BLOCKED, user.getStatus());
        assertNotNull(user.getPendingEmailVerificationToken());
    }

    @Test
    void constructorRejectsMissingValues() {
        var email = new EmailAddress("maria.lopez@example.com");
        var password = new PasswordHash("bcrypt-hash");

        assertThrows(IllegalArgumentException.class,
                () -> new User(null, password, AccessRole.CAREGIVER, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new User(email, null, AccessRole.CAREGIVER, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new User(email, password, null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new User(email, password, AccessRole.CAREGIVER, null));
    }

    @Test
    void requestEmailVerificationRejectsNullToken() {
        var user = newUser();

        assertThrows(IllegalArgumentException.class, () -> user.requestEmailVerification(null));
    }

    private static User newUser() {
        return new User(new EmailAddress("Maria.Lopez@Example.COM"),
                new PasswordHash("bcrypt-hash"), AccessRole.CAREGIVER, NOW);
    }
}
