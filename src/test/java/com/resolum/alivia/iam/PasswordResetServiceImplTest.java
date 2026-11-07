package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.commandservices.PasswordResetServiceImpl;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.ChangePasswordCommand;
import com.resolum.alivia.iam.domain.model.commands.ForgotPasswordCommand;
import com.resolum.alivia.iam.domain.model.commands.ResetPasswordCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String EMAIL = "ana@alivia.pe";
    private static final String CODE = "482915";
    private static final String STRONG = "Clave#2026x";

    private UserRepository users;
    private HashingService hashing;
    private EmailVerificationTokenService tokens;
    private ExternalCommunicationsService communications;
    private PasswordResetServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        hashing = mock(HashingService.class);
        tokens = mock(EmailVerificationTokenService.class);
        communications = mock(ExternalCommunicationsService.class);
        service = new PasswordResetServiceImpl(users, hashing, tokens, communications, CLOCK, 60, 60);
        when(hashing.encode(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        user = new User(new EmailAddress(EMAIL), new PasswordHash("old-hash"), AccessRole.TECHNICIAN, NOW);
        user.verifyEmail();
        user.startSession("refresh-hash", NOW.plusDays(7));
        when(tokens.generateCode(user.getAccountId())).thenReturn(new EmailVerificationTokenService.GeneratedToken(
                CODE, new EmailVerificationToken("code-hash", NOW.plusHours(24))));
        when(tokens.matchesCode(user.getAccountId(), CODE, "code-hash")).thenReturn(true);
        when(users.findByEmailForUpdate(new EmailAddress(EMAIL))).thenReturn(Optional.of(user));
    }

    @Test
    void emailsASixDigitCodeThatExpiresInOneHour() {
        service.handle(new ForgotPasswordCommand(EMAIL));

        assertEquals(NOW.plusMinutes(60), user.getPendingPasswordResetToken().expiresAt());
        verify(communications).sendPasswordResetEmail(EMAIL, CODE);
    }

    @Test
    void staysSilentForUnknownPendingOrTooFrequentRequestsAndFailedDeliveries() {
        service.handle(new ForgotPasswordCommand("nadie@alivia.pe"));
        service.handle(new ForgotPasswordCommand("no-es-correo"));
        service.handle(new ForgotPasswordCommand(null));
        var pending = new User(new EmailAddress("luis@alivia.pe"), new PasswordHash("h"), AccessRole.TECHNICIAN, NOW);
        when(users.findByEmailForUpdate(new EmailAddress("luis@alivia.pe"))).thenReturn(Optional.of(pending));
        service.handle(new ForgotPasswordCommand("luis@alivia.pe"));
        verify(communications, never()).sendPasswordResetEmail(any(), any());

        doThrow(new IllegalStateException("down")).when(communications).sendPasswordResetEmail(any(), any());
        assertDoesNotThrow(() -> service.handle(new ForgotPasswordCommand(EMAIL)));
        service.handle(new ForgotPasswordCommand(EMAIL));
        verify(communications, times(1)).sendPasswordResetEmail(any(), any());
    }

    @Test
    void resetsThePasswordConsumesTheCodeAndEndsTheSession() {
        service.handle(new ForgotPasswordCommand(EMAIL));

        service.handle(new ResetPasswordCommand(EMAIL, CODE, STRONG));

        assertEquals("hash-" + STRONG, user.getPassword().value());
        assertNull(user.getPendingPasswordResetToken());
        assertTrue(user.isRefreshTokenExpiredAt(NOW));
        assertThrows(InvalidVerificationTokenException.class,
                () -> service.handle(new ResetPasswordCommand(EMAIL, CODE, STRONG)));
    }

    @Test
    void voidsTheCodeAfterFiveWrongAttempts() {
        service.handle(new ForgotPasswordCommand(EMAIL));

        for (int attempt = 0; attempt < User.MAX_CODE_ATTEMPTS; attempt++) {
            assertThrows(InvalidVerificationTokenException.class,
                    () -> service.handle(new ResetPasswordCommand(EMAIL, "111111", STRONG)));
        }

        assertThrows(InvalidVerificationTokenException.class,
                () -> service.handle(new ResetPasswordCommand(EMAIL, CODE, STRONG)));
        assertEquals("old-hash", user.getPassword().value());
    }

    @Test
    void changesThePasswordOfTheSignedInUserOnlyWithTheCurrentOne() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(hashing.matches("Actual#2026", "old-hash")).thenReturn(true);
        service.handle(new ForgotPasswordCommand(EMAIL));

        var wrong = assertThrows(SignUpValidationException.class,
                () -> service.handle(new ChangePasswordCommand(7L, "otra", STRONG)));
        assertEquals("currentPassword", wrong.violations().get(0).field());
        assertThrows(SignUpValidationException.class,
                () -> service.handle(new ChangePasswordCommand(7L, "Actual#2026", "debil")));
        assertEquals("old-hash", user.getPassword().value());

        service.handle(new ChangePasswordCommand(7L, "Actual#2026", STRONG));

        assertEquals("hash-" + STRONG, user.getPassword().value());
        assertNull(user.getPendingPasswordResetToken());
        assertFalse(user.isRefreshTokenExpiredAt(NOW));
    }

    @Test
    void rejectsUnknownExpiredOrWeakResets() {
        assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new ResetPasswordCommand("", CODE, STRONG)));
        assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new ResetPasswordCommand(EMAIL, CODE, STRONG)));

        user.requestPasswordReset(new EmailVerificationToken("code-hash", NOW.minusMinutes(1)), NOW.minusHours(2));
        assertThrows(VerificationTokenExpiredException.class, () -> service.handle(new ResetPasswordCommand(EMAIL, CODE, STRONG)));

        user.requestPasswordReset(new EmailVerificationToken("code-hash", NOW.plusMinutes(30)), NOW);
        assertThrows(SignUpValidationException.class, () -> service.handle(new ResetPasswordCommand(EMAIL, CODE, "debil")));
        assertEquals("old-hash", user.getPassword().value());
    }
}
