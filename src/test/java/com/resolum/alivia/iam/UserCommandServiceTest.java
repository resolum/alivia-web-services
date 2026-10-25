package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.commandservices.UserCommandServiceImpl;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.notifications.VerificationEmailService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.exceptions.AccountAlreadyVerifiedException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotActiveException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidCredentialsException;
import com.resolum.alivia.iam.domain.exceptions.InvalidRefreshTokenException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationResendTooSoonException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.RefreshSessionCommand;
import com.resolum.alivia.iam.domain.model.commands.ResendVerificationCommand;
import com.resolum.alivia.iam.domain.model.commands.SignInCommand;
import com.resolum.alivia.iam.domain.model.commands.SignOutCommand;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserCommandServiceTest {

        private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
        private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
        private static final String EMAIL = "maria.lopez@example.com";
        private static final String PASSWORD = "Strong1!";
        private static final String RAW_TOKEN = "raw-token";
        private static final String TOKEN_HASH = "token-hash";
        private static final String VERIFICATION_LINK = "http://localhost:5173/verify-email?token=" + RAW_TOKEN;

        private final UserRepository userRepository = mock(UserRepository.class);
        private final HashingService hashingService = mock(HashingService.class);
        private final EmailVerificationTokenService tokenService = mock(EmailVerificationTokenService.class);
        private final ExternalProfilesService profilesService = mock(ExternalProfilesService.class);
        private final VerificationEmailService emailService = mock(VerificationEmailService.class);
        private final TokenService sessionTokenService = mock(TokenService.class);
        private final UserCommandServiceImpl service = new UserCommandServiceImpl(
                        userRepository, hashingService, tokenService, profilesService, emailService,
                        sessionTokenService, CLOCK, 60);

        @Test
        void signInIssuesTokensForActiveAccount() {
                var user = activeUser();
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(user));
                when(hashingService.matches(PASSWORD, "bcrypt-hash")).thenReturn(true);
                stubSessionTokens();

                var session = service.handle(new SignInCommand(" Maria.Lopez@Example.com ", PASSWORD));

                assertEquals("access-token", session.accessToken());
                assertEquals("refresh-raw", session.refreshToken());
                assertEquals(900L, session.expiresIn());
                assertEquals(user, session.user());
                assertFalse(user.isRefreshTokenExpiredAt(NOW));
                assertEquals("refresh-hash", ReflectionTestUtils.getField(user, "refreshTokenHash"));
        }

        @Test
        void signInRejectsWrongPasswordUnknownEmailAndMissingCredentials() {
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(activeUser()));
                when(hashingService.matches(anyString(), anyString())).thenReturn(false);
                when(userRepository.findByEmail(new EmailAddress("other@example.com"))).thenReturn(Optional.empty());

                assertThrows(InvalidCredentialsException.class, () -> service.handle(new SignInCommand(EMAIL, "wrong")));
                assertThrows(InvalidCredentialsException.class, () -> service.handle(new SignInCommand("other@example.com", PASSWORD)));
                assertThrows(InvalidCredentialsException.class, () -> service.handle(new SignInCommand("not-an-email", PASSWORD)));
                assertThrows(InvalidCredentialsException.class, () -> service.handle(new SignInCommand(null, PASSWORD)));
                assertThrows(InvalidCredentialsException.class, () -> service.handle((SignInCommand) null));
                verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void signInAllowsAccountPendingVerification() {
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(pendingUser(NOW.plusHours(1))));
                when(hashingService.matches(PASSWORD, "bcrypt-hash")).thenReturn(true);
                stubSessionTokens();

                var session = service.handle(new SignInCommand(EMAIL, PASSWORD));

                assertEquals("access-token", session.accessToken());
                assertEquals(UserStatus.PENDING_VERIFICATION, session.user().getStatus());
        }

        @Test
        void refreshRotatesTokenForValidSession() {
                var user = activeUser();
                user.startSession("old-hash", NOW.plusDays(1));
                when(sessionTokenService.hashRefreshToken("old-raw")).thenReturn("old-hash");
                when(userRepository.findByRefreshTokenHash("old-hash")).thenReturn(Optional.of(user));
                stubSessionTokens();

                var session = service.handle(new RefreshSessionCommand("old-raw"));

                assertEquals("refresh-raw", session.refreshToken());
                assertEquals("refresh-hash", ReflectionTestUtils.getField(user, "refreshTokenHash"));
        }

        @Test
        void refreshRejectsMissingUnknownExpiredAndInactiveSessions() {
                assertThrows(InvalidRefreshTokenException.class, () -> service.handle(new RefreshSessionCommand("  ")));
                assertThrows(InvalidRefreshTokenException.class, () -> service.handle((RefreshSessionCommand) null));

                when(sessionTokenService.hashRefreshToken("unknown")).thenReturn("unknown-hash");
                when(userRepository.findByRefreshTokenHash("unknown-hash")).thenReturn(Optional.empty());
                assertThrows(InvalidRefreshTokenException.class, () -> service.handle(new RefreshSessionCommand("unknown")));

                var expired = activeUser();
                expired.startSession("expired-hash", NOW);
                when(sessionTokenService.hashRefreshToken("expired")).thenReturn("expired-hash");
                when(userRepository.findByRefreshTokenHash("expired-hash")).thenReturn(Optional.of(expired));
                assertThrows(InvalidRefreshTokenException.class, () -> service.handle(new RefreshSessionCommand("expired")));

                var blocked = activeUser();
                blocked.startSession("blocked-hash", NOW.plusDays(1));
                ReflectionTestUtils.setField(blocked, "status", UserStatus.BLOCKED);
                when(sessionTokenService.hashRefreshToken("blocked")).thenReturn("blocked-hash");
                when(userRepository.findByRefreshTokenHash("blocked-hash")).thenReturn(Optional.of(blocked));
                assertThrows(AccountNotActiveException.class, () -> service.handle(new RefreshSessionCommand("blocked")));

                verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void signOutDiscardsRefreshTokenAndIgnoresUnknownTokens() {
                var user = activeUser();
                user.startSession("refresh-hash", NOW.plusDays(1));
                when(sessionTokenService.hashRefreshToken("refresh-raw")).thenReturn("refresh-hash");
                when(userRepository.findByRefreshTokenHash("refresh-hash")).thenReturn(Optional.of(user));

                service.handle(new SignOutCommand("refresh-raw"));

                assertTrue(user.isRefreshTokenExpiredAt(NOW));
                verify(userRepository).save(user);

                service.handle(new SignOutCommand(null));
                service.handle((SignOutCommand) null);
                verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        void registersAccountAndSendsVerificationEmail() {
                stubSuccessfulRegistration();

                var result = service.handle(signUpCommand());

                assertEquals(Long.valueOf(42L), result.getId());
                assertEquals(EMAIL, result.getEmail().email());
                assertEquals("bcrypt-hash", result.getPassword().value());
                assertEquals(AccessRole.CAREGIVER, result.getAccessRole());
                assertEquals(UserStatus.PENDING_VERIFICATION, result.getStatus());
                assertFalse(result.getEmailVerified());
                assertEquals(Integer.valueOf(0), result.getFailedAccessAttempts());
                assertNotNull(result.getTermsAcceptedAt());
                assertNotNull(result.getPendingEmailVerificationToken());
                assertEquals(TOKEN_HASH, result.getPendingEmailVerificationToken().value());
                assertEquals(NOW.plusHours(24), result.getPendingEmailVerificationToken().expiresAt());
                verify(hashingService).encode(PASSWORD);
                verify(profilesService).createProfile(42L, new ProfileData("Maria Lopez", "+51987654321"));
                verify(emailService).sendVerificationEmail(EMAIL, RAW_TOKEN, VERIFICATION_LINK);
                verify(userRepository).save(any(User.class));
        }

        @Test
        void rejectsEmailAlreadyRegistered() {
                when(userRepository.existsByEmail(any(EmailAddress.class))).thenReturn(true);

                assertThrows(EmailAlreadyRegisteredException.class, () -> service.handle(signUpCommand()));

                verify(userRepository, never()).save(any(User.class));
                verifyNoInteractions(hashingService, tokenService, profilesService, emailService);
        }

        @Test
        void rejectsWeakPassword() {
                var command = new SignUpCommand(EMAIL, "short", "Maria Lopez", "+51987654321", true);

                var exception = assertThrows(SignUpValidationException.class, () -> service.handle(command));

                assertEquals("password", exception.violations().getFirst().field());
                assertEquals("password_weak", exception.violations().getFirst().code());
                verifyNoInteractions(userRepository, hashingService, tokenService,
                                profilesService, emailService);
        }

        @Test
        void rejectsUnacceptedTerms() {
                var command = new SignUpCommand(EMAIL, PASSWORD, "Maria Lopez", "+51987654321", false);

                var exception = assertThrows(SignUpValidationException.class, () -> service.handle(command));

                assertEquals("acceptedTerms", exception.violations().getFirst().field());
                assertEquals("terms_not_accepted", exception.violations().getFirst().code());
                verifyNoInteractions(userRepository, hashingService, tokenService,
                                profilesService, emailService);
        }

        @Test
        void rejectsMissingProfileData() {
                var missingFullName = new SignUpCommand(EMAIL, PASSWORD, " ", "+51987654321", true);
                var missingPhone = new SignUpCommand(EMAIL, PASSWORD, "Maria Lopez", "", true);

                var fullNameError = assertThrows(SignUpValidationException.class,
                                () -> service.handle(missingFullName));
                var phoneError = assertThrows(SignUpValidationException.class,
                                () -> service.handle(missingPhone));

                assertEquals("fullName", fullNameError.violations().getFirst().field());
                assertEquals("required", fullNameError.violations().getFirst().code());
                assertEquals("phoneNumber", phoneError.violations().getFirst().field());
                assertEquals("required", phoneError.violations().getFirst().code());
                verifyNoInteractions(userRepository, hashingService, tokenService,
                                profilesService, emailService);
        }

        @Test
        void registrationFailsWhenEmailProviderFails() {
                stubSuccessfulRegistration();
                doThrow(new IllegalStateException("mail provider down"))
                                .when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

                var exception = assertThrows(RegistrationUnavailableException.class,
                                () -> service.handle(signUpCommand()));

                assertInstanceOf(IllegalStateException.class, exception.getCause());
                verify(userRepository).save(any(User.class));
        }

        @Test
        void registrationFailsWhenProfilesServiceFails() {
                stubSuccessfulRegistration();
                doThrow(new IllegalStateException("profiles down"))
                                .when(profilesService).createProfile(anyLong(), any(ProfileData.class));

                var exception = assertThrows(RegistrationUnavailableException.class,
                                () -> service.handle(signUpCommand()));

                assertInstanceOf(IllegalStateException.class, exception.getCause());
                verify(userRepository).save(any(User.class));
                verifyNoInteractions(emailService);
        }

        @Test
        void verifiesEmailAndConsumesToken() {
                var user = pendingUser(NOW.plusHours(1));
                when(tokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                when(userRepository.findByEmailVerificationTokenHash(TOKEN_HASH))
                                .thenReturn(Optional.of(user));
                when(tokenService.matches(RAW_TOKEN, TOKEN_HASH)).thenReturn(true);
                when(userRepository.save(user)).thenAnswer(invocation -> {
                        assignId(invocation.getArgument(0), 42L);
                        return invocation.getArgument(0);
                });

                var result = service.handle(new VerifyEmailCommand(RAW_TOKEN));

                assertEquals(UserStatus.ACTIVE, result.getStatus());
                assertTrue(result.getEmailVerified());
                assertNull(result.getPendingEmailVerificationToken());
                verify(userRepository).save(user);
        }

        @Test
        void rejectsIncorrectToken() {
                when(tokenService.hash("invented")).thenReturn("unknown-hash");
                when(userRepository.findByEmailVerificationTokenHash("unknown-hash"))
                                .thenReturn(Optional.empty());

                assertThrows(InvalidVerificationTokenException.class,
                                () -> service.handle(new VerifyEmailCommand("invented")));

                verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void rejectsBlankTokenWithoutQueryingPersistence() {
                assertThrows(InvalidVerificationTokenException.class,
                                () -> service.handle(new VerifyEmailCommand("   ")));

                verifyNoInteractions(userRepository, tokenService);
        }

        @Test
        void rejectsExpiredTokenAndKeepsAccountPending() {
                var user = pendingUser(NOW.minusSeconds(1));
                when(tokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                when(userRepository.findByEmailVerificationTokenHash(TOKEN_HASH))
                                .thenReturn(Optional.of(user));
                when(tokenService.matches(RAW_TOKEN, TOKEN_HASH)).thenReturn(true);

                assertThrows(VerificationTokenExpiredException.class,
                                () -> service.handle(new VerifyEmailCommand(RAW_TOKEN)));

                assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
                assertNotNull(user.getPendingEmailVerificationToken());
                verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void rejectsReusedToken() {
                var user = pendingUser(NOW.plusHours(1));
                when(tokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                when(userRepository.findByEmailVerificationTokenHash(TOKEN_HASH))
                                .thenReturn(Optional.of(user), Optional.empty());
                when(tokenService.matches(RAW_TOKEN, TOKEN_HASH)).thenReturn(true);
                when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

                service.handle(new VerifyEmailCommand(RAW_TOKEN));

                assertThrows(InvalidVerificationTokenException.class,
                                () -> service.handle(new VerifyEmailCommand(RAW_TOKEN)));
                assertEquals(UserStatus.ACTIVE, user.getStatus());
                verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        void rejectsVerificationForBlockedAccountWithPendingToken() {
                var user = pendingUser(NOW.plusHours(1));
                ReflectionTestUtils.setField(user, "status", UserStatus.BLOCKED);
                when(tokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                when(userRepository.findByEmailVerificationTokenHash(TOKEN_HASH))
                                .thenReturn(Optional.of(user));
                when(tokenService.matches(RAW_TOKEN, TOKEN_HASH)).thenReturn(true);

                assertThrows(AccountNotPendingVerificationException.class,
                                () -> service.handle(new VerifyEmailCommand(RAW_TOKEN)));

                assertEquals(UserStatus.BLOCKED, user.getStatus());
                assertNotNull(user.getPendingEmailVerificationToken());
                verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void registersAdministratorWithSignUpCommand() {
                stubSuccessfulRegistration();

                var cmd = new SignUpCommand(EMAIL, PASSWORD, "Admin User", "+51987654321", true,
                                AccessRole.ADMINISTRATOR, null, null);
                var result = service.handle(cmd);

                assertEquals(AccessRole.ADMINISTRATOR, result.getAccessRole());
                verify(profilesService).createProfile(42L, new ProfileData("Admin User", "+51987654321", null, null));
        }

        @Test
        void registersEmployeeWithBusinessId() {
                stubSuccessfulRegistration();

                var cmd = new SignUpCommand(EMAIL, PASSWORD, "Emp User", "+51987654321", true,
                                AccessRole.TECHNICIAN, 100L, null);
                var result = service.handle(cmd);

                assertEquals(AccessRole.TECHNICIAN, result.getAccessRole());
                verify(profilesService).createProfile(42L, new ProfileData("Emp User", "+51987654321", 100L, null));
        }

        @Test
        void registersAssistedPersonWithFamiliarId() {
                stubSuccessfulRegistration();

                var cmd = new SignUpCommand(EMAIL, PASSWORD, "Assisted", "+51987654321", true,
                                AccessRole.ASSISTED_PERSON, null, 200L);
                var result = service.handle(cmd);

                assertEquals(AccessRole.ASSISTED_PERSON, result.getAccessRole());
                verify(profilesService).createProfile(42L, new ProfileData("Assisted", "+51987654321", null, 200L));
        }

        @Test
        void rejectsNullCommands() {
                assertThrows(IllegalArgumentException.class, () -> service.handle((SignUpCommand) null));
                assertThrows(InvalidVerificationTokenException.class, () -> service.handle((VerifyEmailCommand) null));
                assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new VerifyEmailCommand(null)));
        }

        @Test
        void rejectsMissingProfileFieldsAndInvalidEmail() {
                assertThrows(SignUpValidationException.class, () -> service.handle(
                                new SignUpCommand(EMAIL, PASSWORD, null, "+51987654321", true)));
                assertThrows(SignUpValidationException.class, () -> service.handle(
                                new SignUpCommand(EMAIL, PASSWORD, "  ", "+51987654321", true)));
                assertThrows(SignUpValidationException.class, () -> service.handle(
                                new SignUpCommand(EMAIL, PASSWORD, "Name", null, true)));
                assertThrows(SignUpValidationException.class, () -> service.handle(
                                new SignUpCommand(EMAIL, PASSWORD, "Name", "  ", true)));
                assertThrows(SignUpValidationException.class, () -> service.handle(
                                new SignUpCommand("invalid-email", PASSWORD, "Name", "+51987654321", true)));
        }

        @Test
        void rejectsVerificationWhenPendingTokenIsNullOrDoesNotMatch() {
                var userWithoutToken = new User(new EmailAddress(EMAIL), new PasswordHash("bcrypt-hash"), AccessRole.CAREGIVER, NOW);
                when(tokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                when(userRepository.findByEmailVerificationTokenHash(TOKEN_HASH)).thenReturn(Optional.of(userWithoutToken));

                assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new VerifyEmailCommand(RAW_TOKEN)));

                var userWithToken = pendingUser(NOW.plusHours(1));
                when(userRepository.findByEmailVerificationTokenHash(TOKEN_HASH)).thenReturn(Optional.of(userWithToken));
                when(tokenService.matches(RAW_TOKEN, TOKEN_HASH)).thenReturn(false);

                assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new VerifyEmailCommand(RAW_TOKEN)));
        }

        @Test
        void resendVerificationReplacesTokenAndSendsNewLink() {
                var user = new User(new EmailAddress(EMAIL), new PasswordHash("bcrypt-hash"), AccessRole.CAREGIVER, NOW);
                user.requestEmailVerification(new EmailVerificationToken("old-hash", NOW.plusHours(24)), NOW.minusSeconds(61));
                assignId(user, 42L);
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(user));
                stubSuccessfulRegistration();

                service.handle(new ResendVerificationCommand(EMAIL));

                assertEquals(TOKEN_HASH, user.getPendingEmailVerificationToken().value());
                verify(emailService).sendVerificationEmail(EMAIL, RAW_TOKEN, VERIFICATION_LINK);
                assertEquals(60L, user.secondsUntilVerificationResend(NOW, java.time.Duration.ofSeconds(60)));
        }

        @Test
        void resendVerificationRejectsRequestWithinCooldown() {
                var user = pendingUser(NOW.plusHours(24));
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(user));

                var exception = assertThrows(VerificationResendTooSoonException.class,
                                () -> service.handle(new ResendVerificationCommand(EMAIL)));

                assertEquals(60L, exception.retryAfterSeconds());
                assertEquals(TOKEN_HASH, user.getPendingEmailVerificationToken().value());
                verifyNoInteractions(emailService);
        }

        @Test
        void resendVerificationRejectsActiveAccount() {
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(activeUser()));

                assertThrows(AccountAlreadyVerifiedException.class,
                                () -> service.handle(new ResendVerificationCommand(EMAIL)));
                verifyNoInteractions(emailService);
        }

        @Test
        void resendVerificationSilentlyIgnoresUnknownInvalidAndBlockedAccounts() {
                var blocked = pendingUser(NOW.plusHours(24));
                ReflectionTestUtils.setField(blocked, "status", UserStatus.BLOCKED);
                when(userRepository.findByEmail(new EmailAddress(EMAIL))).thenReturn(Optional.of(blocked));
                when(userRepository.findByEmail(new EmailAddress("other@example.com"))).thenReturn(Optional.empty());

                service.handle(new ResendVerificationCommand(EMAIL));
                service.handle(new ResendVerificationCommand("other@example.com"));
                service.handle(new ResendVerificationCommand("not-an-email"));
                service.handle(new ResendVerificationCommand(null));
                service.handle((ResendVerificationCommand) null);

                verify(userRepository, never()).save(any(User.class));
                verifyNoInteractions(emailService);
        }

        private void stubSuccessfulRegistration() {
                when(userRepository.existsByEmail(any(EmailAddress.class))).thenReturn(false);
                when(hashingService.encode(PASSWORD)).thenReturn("bcrypt-hash");
                when(tokenService.generate()).thenReturn(new EmailVerificationTokenService.GeneratedToken(
                                RAW_TOKEN, new EmailVerificationToken(TOKEN_HASH, NOW.plusHours(24))));
                when(tokenService.buildVerificationLink(RAW_TOKEN)).thenReturn(VERIFICATION_LINK);
                when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                        assignId(invocation.getArgument(0), 42L);
                        return invocation.getArgument(0);
                });
        }

        private static SignUpCommand signUpCommand() {
                return new SignUpCommand("  Maria.Lopez@Example.COM ", PASSWORD,
                                "Maria Lopez", "+51987654321", true);
        }

        private static User pendingUser(LocalDateTime expiresAt) {
                var user = new User(new EmailAddress(EMAIL), new PasswordHash("bcrypt-hash"),
                                AccessRole.CAREGIVER, NOW);
                user.requestEmailVerification(new EmailVerificationToken(TOKEN_HASH, expiresAt), NOW);
                return user;
        }

        private void stubSessionTokens() {
                when(sessionTokenService.generateRefreshToken()).thenReturn(
                                new TokenService.GeneratedRefreshToken("refresh-raw", "refresh-hash", NOW.plusDays(7)));
                when(sessionTokenService.generateAccessToken(any(User.class))).thenReturn("access-token");
                when(sessionTokenService.getAccessTokenExpirationSeconds()).thenReturn(900L);
                when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private static User activeUser() {
                var user = pendingUser(NOW.plusHours(1));
                user.verifyEmail();
                assignId(user, 42L);
                return user;
        }

        private static void assignId(User user, long id) {
                ReflectionTestUtils.setField(user, "id", id);
        }
}
