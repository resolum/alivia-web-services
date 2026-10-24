package com.resolum.alivia.iam.application.internal.commandservices;

import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.notifications.VerificationEmailService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.PasswordPolicy;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Implementation of {@link UserCommandService}.
 *
 * <p>Each command runs in a single transaction. Registration is all-or-nothing: a
 * failure in the Profiles context or in the verification email provider rolls the
 * account back and surfaces as {@link RegistrationUnavailableException}.</p>
 */
@Slf4j
@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final ExternalProfilesService externalProfilesService;
    private final VerificationEmailService verificationEmailService;
    private final Clock clock;

    /**
     * Creates the command service.
     *
     * @param userRepository               the user persistence port
     * @param hashingService               the password hashing port
     * @param emailVerificationTokenService the verification token port
     * @param externalProfilesService      the Profiles anti-corruption port
     * @param verificationEmailService     the verification email port
     * @param clock                        the clock used for timestamps
     */
    public UserCommandServiceImpl(UserRepository userRepository,
                                  HashingService hashingService,
                                  EmailVerificationTokenService emailVerificationTokenService,
                                  ExternalProfilesService externalProfilesService,
                                  VerificationEmailService verificationEmailService,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.externalProfilesService = externalProfilesService;
        this.verificationEmailService = verificationEmailService;
        this.clock = clock;
    }

    /**
     * Registers a new account and requests its email verification.
     *
     * @param command the sign-up command
     * @return the registered user
     * @throws SignUpValidationException        when the password or the terms acceptance is invalid
     * @throws EmailAlreadyRegisteredException  when the email address already exists
     * @throws RegistrationUnavailableException when the profile or the email provider fails
     */
    @Override
    @Transactional
    public User handle(SignUpCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Sign-up command is required.");
        }
        PasswordPolicy.validate(command.password());
        if (!command.termsAccepted()) {
            throw new SignUpValidationException(
                    "acceptedTerms", SignUpValidationException.TERMS_NOT_ACCEPTED);
        }
        var email = toEmailAddress(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        var password = new PasswordHash(hashingService.encode(command.password()));
        var generatedToken = emailVerificationTokenService.generate();
        // PENDING(confirm): iam.md has no distinct role for the primary family member.
        var user = new User(email, password, AccessRole.CAREGIVER, LocalDateTime.now(clock));
        user.requestEmailVerification(generatedToken.token());
        var savedUser = userRepository.save(user);
        createProfile(savedUser, command);
        sendVerificationEmail(savedUser, generatedToken.rawToken());
        log.info("Registered user with id {}", savedUser.getId());
        return savedUser;
    }

    /**
     * Verifies the email address of an account.
     *
     * @param command the verify-email command
     * @return the verified user
     * @throws InvalidVerificationTokenException  when the account or the pending token does not match
     * @throws VerificationTokenExpiredException  when the pending token has expired
     */
    @Override
    @Transactional
    public User handle(VerifyEmailCommand command) {
        if (command == null || command.accountId() == null || command.verificationToken() == null
                || command.verificationToken().isBlank()) {
            throw new InvalidVerificationTokenException();
        }
        var user = userRepository.findByIdForUpdate(command.accountId())
                .orElseThrow(InvalidVerificationTokenException::new);
        var pendingToken = user.getPendingEmailVerificationToken();
        if (pendingToken == null
                || !emailVerificationTokenService.matches(command.verificationToken(), pendingToken.value())) {
            throw new InvalidVerificationTokenException();
        }
        if (pendingToken.isExpiredAt(LocalDateTime.now(clock))) {
            throw new VerificationTokenExpiredException();
        }
        user.verifyEmail();
        var savedUser = userRepository.save(user);
        log.info("Verified email for user id {}", savedUser.getId());
        return savedUser;
    }

    private EmailAddress toEmailAddress(String email) {
        try {
            return new EmailAddress(email);
        } catch (IllegalArgumentException exception) {
            throw new SignUpValidationException("email", SignUpValidationException.INVALID_EMAIL);
        }
    }

    private void createProfile(User user, SignUpCommand command) {
        try {
            externalProfilesService.createProfile(user.getId(), command.fullName(), command.phoneNumber());
        } catch (RuntimeException exception) {
            throw new RegistrationUnavailableException(exception);
        }
    }

    private void sendVerificationEmail(User user, String rawToken) {
        try {
            var verificationLink = emailVerificationTokenService.buildVerificationLink(rawToken);
            verificationEmailService.sendVerificationEmail(
                    user.getEmail().email(), rawToken, verificationLink);
        } catch (RuntimeException exception) {
            throw new RegistrationUnavailableException(exception);
        }
    }
}
