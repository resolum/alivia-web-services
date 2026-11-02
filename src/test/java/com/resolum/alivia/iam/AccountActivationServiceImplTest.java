package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.commandservices.AccountActivationServiceImpl;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.ActivateAccountCommand;
import com.resolum.alivia.iam.domain.model.commands.InviteCompanyUserCommand;
import com.resolum.alivia.iam.domain.model.queries.GetAccountActivationQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
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

class AccountActivationServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String STRONG = "Clave#2026x";

    private UserRepository users;
    private HashingService hashing;
    private EmailVerificationTokenService tokens;
    private ExternalCommunicationsService communications;
    private ExternalProfilesService profiles;
    private AccountActivationServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        hashing = mock(HashingService.class);
        tokens = mock(EmailVerificationTokenService.class);
        communications = mock(ExternalCommunicationsService.class);
        profiles = mock(ExternalProfilesService.class);
        service = new AccountActivationServiceImpl(users, hashing, tokens, communications, profiles, CLOCK, 48,
                "http://localhost:4200/activate-account");
        when(hashing.encode(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0).toString().length());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokens.generate()).thenReturn(new EmailVerificationTokenService.GeneratedToken("raw token",
                new EmailVerificationToken("token-hash", NOW.plusHours(24))));
        when(tokens.hash("raw token")).thenReturn("token-hash");
        when(tokens.matches("raw token", "token-hash")).thenReturn(true);
    }

    @Test
    void invitesWithoutAKnownPasswordAndEmailsTheActivationLink() {
        var user = service.handle(new InviteCompanyUserCommand("ana@alivia.pe", "Ana Pérez", "Soporte técnico",
                AccessRole.TECHNICIAN, 30L));

        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
        assertEquals(30L, user.getProfileId());
        assertEquals(NOW.plusHours(48), user.getPendingEmailVerificationToken().expiresAt());
        verify(communications).sendAccountInvitationEmail("ana@alivia.pe", "Ana Pérez", "Soporte técnico",
                "http://localhost:4200/activate-account?token=raw+token", 48);
    }

    @Test
    void rejectsDuplicatedOrInvalidEmailsAndFailedDeliveries() {
        assertThrows(SignUpValidationException.class, () -> service.handle(new InviteCompanyUserCommand(
                "no-es-correo", "Ana", "Soporte técnico", AccessRole.TECHNICIAN, 30L)));
        when(users.existsByEmail(any(EmailAddress.class))).thenReturn(true);
        assertThrows(EmailAlreadyRegisteredException.class, () -> service.handle(new InviteCompanyUserCommand(
                "ana@alivia.pe", "Ana", "Soporte técnico", AccessRole.TECHNICIAN, 30L)));
        when(users.existsByEmail(any(EmailAddress.class))).thenReturn(false);
        doThrow(new IllegalStateException("down")).when(communications)
                .sendAccountInvitationEmail(any(), any(), any(), any(), anyInt());
        assertThrows(RegistrationUnavailableException.class, () -> service.handle(new InviteCompanyUserCommand(
                "ana@alivia.pe", "Ana", "Soporte técnico", AccessRole.TECHNICIAN, null)));
        assertThrows(IllegalArgumentException.class, () -> service.handle(new InviteCompanyUserCommand(
                "ana@alivia.pe", "Ana", "Soporte técnico", null, 30L)));
    }

    @Test
    void showsWhoTheLinkBelongsToAndActivatesTheAccount() {
        var user = invited(NOW.plusHours(48));
        when(users.findByEmailVerificationTokenHash("token-hash")).thenReturn(Optional.of(user));
        when(profiles.fetchStaffFullName(30L)).thenReturn(Optional.of("Ana Pérez"));

        var activation = service.handle(new GetAccountActivationQuery("raw token"));
        assertEquals("ana@alivia.pe", activation.email());
        assertEquals("Ana Pérez", activation.fullName());

        var activated = service.handle(new ActivateAccountCommand("raw token", STRONG));
        assertEquals(UserStatus.ACTIVE, activated.getStatus());
        assertTrue(activated.getEmailVerified());
        assertEquals("hash-" + STRONG.length(), activated.getPassword().value());
        assertNull(activated.getPendingEmailVerificationToken());
    }

    @Test
    void rejectsMissingUnknownExpiredOrUsedLinksAndWeakPasswords() {
        assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new ActivateAccountCommand(" ", STRONG)));
        assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new GetAccountActivationQuery("raw token")));

        when(users.findByEmailVerificationTokenHash("token-hash")).thenReturn(Optional.of(invited(NOW.minusMinutes(1))));
        assertThrows(VerificationTokenExpiredException.class, () -> service.handle(new ActivateAccountCommand("raw token", STRONG)));

        when(users.findByEmailVerificationTokenHash("token-hash")).thenReturn(Optional.of(invited(NOW.plusHours(1))));
        assertThrows(SignUpValidationException.class, () -> service.handle(new ActivateAccountCommand("raw token", "debil")));
        when(tokens.matches("raw token", "token-hash")).thenReturn(false);
        assertThrows(InvalidVerificationTokenException.class, () -> service.handle(new ActivateAccountCommand("raw token", STRONG)));
    }

    @Test
    void anActivatedAccountCannotBeActivatedAgain() {
        var user = invited(NOW.plusHours(1));
        user.activate(new PasswordHash("hash"), NOW);
        assertThrows(AccountNotPendingVerificationException.class, () -> user.activate(new PasswordHash("hash"), NOW));
        assertThrows(IllegalArgumentException.class, () -> user.activate(null, NOW));
    }

    @Test
    void requiresAValidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new AccountActivationServiceImpl(users, hashing, tokens,
                communications, profiles, CLOCK, 0, "http://x"));
        assertThrows(IllegalArgumentException.class, () -> new AccountActivationServiceImpl(users, hashing, tokens,
                communications, profiles, CLOCK, 48, " "));
    }

    private static User invited(LocalDateTime expiresAt) {
        var user = new User(new EmailAddress("ana@alivia.pe"), new PasswordHash("unusable"), AccessRole.TECHNICIAN, NOW);
        user.requestEmailVerification(new EmailVerificationToken("token-hash", expiresAt), NOW);
        user.assignProfile(30L);
        return user;
    }
}
