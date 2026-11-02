package com.resolum.alivia.iam.application.internal.commandservices;

import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.ActivateAccountCommand;
import com.resolum.alivia.iam.domain.model.commands.InviteCompanyUserCommand;
import com.resolum.alivia.iam.domain.model.queries.GetAccountActivationQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccountActivation;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.AccountActivationService;
import com.resolum.alivia.iam.domain.services.PasswordPolicy;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Invitation flow for staff. The account is created with an unusable random password and the
 * pending email token of the verification flow, valid for {@code alivia.iam.account-activation.expiration-hours};
 * activating it sets the password chosen by its owner and verifies the email at once.
 */
@Slf4j
@Service
public class AccountActivationServiceImpl implements AccountActivationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final EmailVerificationTokenService tokenService;
    private final ExternalCommunicationsService externalCommunicationsService;
    private final ExternalProfilesService externalProfilesService;
    private final Clock clock;
    private final int expirationHours;
    private final String linkBaseUrl;

    public AccountActivationServiceImpl(UserRepository userRepository, HashingService hashingService,
                                        EmailVerificationTokenService tokenService,
                                        ExternalCommunicationsService externalCommunicationsService,
                                        ExternalProfilesService externalProfilesService, Clock clock,
                                        @Value("${alivia.iam.account-activation.expiration-hours:48}") int expirationHours,
                                        @Value("${alivia.iam.account-activation.link-base-url}") String linkBaseUrl) {
        if (expirationHours <= 0) throw new IllegalArgumentException("Activation link expiration must be positive.");
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) throw new IllegalArgumentException("Activation link base URL is required.");
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.externalCommunicationsService = externalCommunicationsService;
        this.externalProfilesService = externalProfilesService;
        this.clock = clock;
        this.expirationHours = expirationHours;
        this.linkBaseUrl = linkBaseUrl.trim();
    }

    @Override
    @Transactional
    public User handle(InviteCompanyUserCommand command) {
        if (command == null || command.role() == null) throw new IllegalArgumentException("Invitation and role are required.");
        var email = toEmailAddress(command.email());
        if (userRepository.existsByEmail(email)) {
            log.warn("Invitation rejected: email is already registered");
            throw new EmailAlreadyRegisteredException();
        }
        var now = LocalDateTime.now(clock);
        var generated = tokenService.generate();
        var token = new EmailVerificationToken(generated.token().value(), now.plusHours(expirationHours));
        var user = new User(email, new PasswordHash(hashingService.encode(unusablePassword())), command.role(), now);
        user.requestEmailVerification(token, now);
        if (command.profileId() != null) user.assignProfile(command.profileId());
        var savedUser = userRepository.save(user);
        sendInvitation(savedUser, command, generated.rawToken());
        log.info("Invited account id {} with role {}", savedUser.getId(), savedUser.getAccessRole());
        return savedUser;
    }

    @Override
    @Transactional
    public User handle(ActivateAccountCommand command) {
        var user = pendingUser(command == null ? null : command.activationToken());
        PasswordPolicy.validate(command.password());
        user.activate(new PasswordHash(hashingService.encode(command.password())), LocalDateTime.now(clock));
        var savedUser = userRepository.save(user);
        log.info("Activated account id {}", savedUser.getId());
        return savedUser;
    }

    @Override
    // Token lookup takes a pessimistic write lock so activation cannot race with
    // this inspection. PostgreSQL does not allow that lock in a read-only transaction.
    @Transactional
    public AccountActivation handle(GetAccountActivationQuery query) {
        var user = pendingUser(query == null ? null : query.activationToken());
        var email = user.getEmail().email();
        return new AccountActivation(email, externalProfilesService.fetchStaffFullName(user.getProfileId()).orElse(email),
                user.getPendingEmailVerificationToken().expiresAt());
    }

    /** The account the link belongs to, while its token is still valid and unused. */
    private User pendingUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidVerificationTokenException();
        var user = userRepository.findByEmailVerificationTokenHash(tokenService.hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);
        var pending = user.getPendingEmailVerificationToken();
        if (pending == null || !tokenService.matches(rawToken, pending.value())) throw new InvalidVerificationTokenException();
        if (pending.isExpiredAt(LocalDateTime.now(clock))) throw new VerificationTokenExpiredException();
        return user;
    }

    private void sendInvitation(User user, InviteCompanyUserCommand command, String rawToken) {
        var link = linkBaseUrl + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        try {
            externalCommunicationsService.sendAccountInvitationEmail(user.getEmail().email(), command.fullName(),
                    command.roleLabel(), link, expirationHours);
        } catch (RuntimeException exception) {
            log.error("Invitation email delivery failed for account id {}: {}",
                    user.getId(), exception.getClass().getSimpleName());
            throw new RegistrationUnavailableException(exception);
        }
    }

    private static EmailAddress toEmailAddress(String email) {
        try {
            return new EmailAddress(email);
        } catch (IllegalArgumentException exception) {
            throw new SignUpValidationException("email", SignUpValidationException.INVALID_EMAIL);
        }
    }

    /** Nobody knows this password: the account can only be used after its owner activates it. */
    private static String unusablePassword() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
