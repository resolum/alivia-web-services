package com.resolum.alivia.iam.application.internal.commandservices;

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
import com.resolum.alivia.iam.domain.model.valueobjects.AuthSession;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.PasswordPolicy;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link UserCommandService}.
 *
 * <p>
 * Each command runs in a single transaction. Registration is all-or-nothing: a
 * failure in the Profiles context or in the verification email provider rolls
 * the
 * account back and surfaces as {@link RegistrationUnavailableException}.
 * </p>
 */
@Slf4j
@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final ExternalProfilesService externalProfilesService;
    private final VerificationEmailService verificationEmailService;
    private final TokenService tokenService;
    private final Clock clock;
    private final Duration resendCooldown;

    /**
     * Creates the command service.
     *
     * @param userRepository                the user persistence port
     * @param hashingService                the password hashing port
     * @param emailVerificationTokenService the verification token port
     * @param externalProfilesService       the Profiles anti-corruption port
     * @param verificationEmailService      the verification email port
     * @param tokenService                  the session token port
     * @param clock                         the clock used for timestamps
     * @param resendCooldownSeconds         the minimum seconds between two verification emails
     */
    public UserCommandServiceImpl(UserRepository userRepository,
            HashingService hashingService,
            EmailVerificationTokenService emailVerificationTokenService,
            ExternalProfilesService externalProfilesService,
            @Qualifier("verificationEmailService") VerificationEmailService verificationEmailService,
            TokenService tokenService,
            Clock clock,
            @Value("${alivia.iam.email-verification.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.externalProfilesService = externalProfilesService;
        this.verificationEmailService = verificationEmailService;
        this.tokenService = tokenService;
        this.clock = clock;
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
    }

    /**
     * Registers a new account and requests its email verification.
     *
     * @param command the sign-up command
     * @return the registered user
     * @throws SignUpValidationException        when the password or the terms
     *                                          acceptance is invalid
     * @throws EmailAlreadyRegisteredException  when the email address already
     *                                          exists
     * @throws RegistrationUnavailableException when the profile or the email
     *                                          provider fails
     */
    @Override
    @Transactional
    public User handle(SignUpCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Sign-up command is required.");
        }
        log.debug("Handling sign-up command");
        validatePassword(command.password());
        if (!command.termsAccepted()) {
            log.warn("Sign-up rejected: terms were not accepted");
            throw new SignUpValidationException(
                    "acceptedTerms", SignUpValidationException.TERMS_NOT_ACCEPTED);
        }
        validateProfileData(command);
        var email = toEmailAddress(command.email());
        if (userRepository.existsByEmail(email)) {
            log.warn("Sign-up rejected: email is already registered");
            throw new EmailAlreadyRegisteredException();
        }
        var password = new PasswordHash(hashingService.encode(command.password()));
        var generatedToken = emailVerificationTokenService.generate();
        AccessRole role = command.role() != null ? command.role() : AccessRole.CAREGIVER;
        var now = LocalDateTime.now(clock);
        var user = new User(email, password, role, now);
        user.requestEmailVerification(generatedToken.token(), now);
        var savedUser = userRepository.save(user);
        createProfile(savedUser, command);
        sendVerificationEmail(savedUser, generatedToken.rawToken());
        log.info("Registered account id {} with role {} and status {}",
                savedUser.getId(), savedUser.getAccessRole(), savedUser.getStatus());
        return savedUser;
    }

    /**
     * Verifies the email address of an account.
     *
     * @param command the verify-email command
     * @return the verified user
     * @throws InvalidVerificationTokenException when the account or the pending
     *                                           token does not match
     * @throws VerificationTokenExpiredException when the pending token has expired
     */
    @Override
    @Transactional
    public User handle(VerifyEmailCommand command) {
        if (command == null || command.verificationToken() == null
                || command.verificationToken().isBlank()) {
            log.warn("Email verification rejected: token is missing");
            throw new InvalidVerificationTokenException();
        }
        log.debug("Handling email verification command");
        var tokenHash = emailVerificationTokenService.hash(command.verificationToken());
        var user = userRepository.findByEmailVerificationTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Email verification rejected: token not found or already used");
                    return new InvalidVerificationTokenException();
                });
        var pendingToken = user.getPendingEmailVerificationToken();
        if (pendingToken == null
                || !emailVerificationTokenService.matches(command.verificationToken(), pendingToken.value())) {
            log.warn("Email verification rejected: token does not match account id {}", user.getId());
            throw new InvalidVerificationTokenException();
        }
        if (pendingToken.isExpiredAt(LocalDateTime.now(clock))) {
            log.warn("Email verification rejected: token expired for account id {}", user.getId());
            throw new VerificationTokenExpiredException();
        }
        try {
            user.verifyEmail();
        } catch (AccountNotPendingVerificationException exception) {
            log.warn("Email verification rejected: account id {} is not pending verification",
                    user.getId());
            throw exception;
        }
        var savedUser = userRepository.save(user);
        log.info("Verified email for account id {}", savedUser.getId());
        return savedUser;
    }

    /**
     * Replaces the pending verification token and sends a new link. Unknown emails and
     * accounts that are neither pending nor active are ignored so the endpoint does not
     * reveal which accounts exist.
     *
     * @param command the resend-verification command
     * @throws AccountAlreadyVerifiedException     when the account is already active
     * @throws VerificationResendTooSoonException when the previous link is too recent
     * @throws RegistrationUnavailableException    when the email provider fails
     */
    @Override
    @Transactional
    public void handle(ResendVerificationCommand command) {
        if (command == null || command.email() == null) {
            return;
        }
        log.debug("Handling verification resend command");
        var user = findUserByEmail(command.email()).orElse(null);
        if (user == null) {
            log.info("Verification resend ignored: no account for the given email");
            return;
        }
        if (user.getStatus() == UserStatus.ACTIVE) {
            log.warn("Verification resend rejected: account id {} is already active", user.getId());
            throw new AccountAlreadyVerifiedException();
        }
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            log.warn("Verification resend ignored: account id {} has status {}", user.getId(), user.getStatus());
            return;
        }
        var now = LocalDateTime.now(clock);
        long retryAfterSeconds = user.secondsUntilVerificationResend(now, resendCooldown);
        if (retryAfterSeconds > 0) {
            log.warn("Verification resend rejected: account id {} must wait {}s", user.getId(), retryAfterSeconds);
            throw new VerificationResendTooSoonException(retryAfterSeconds);
        }
        var generatedToken = emailVerificationTokenService.generate();
        user.requestEmailVerification(generatedToken.token(), now);
        var savedUser = userRepository.save(user);
        sendVerificationEmail(savedUser, generatedToken.rawToken());
        log.info("Resent verification email for account id {}", savedUser.getId());
    }

    /**
     * Authenticates an account and opens a session.
     *
     * @param command the sign-in command
     * @return the issued tokens
     * @throws InvalidCredentialsException when the email or the password does not match
     * @throws AccountNotActiveException   when the account is not active
     */
    @Override
    @Transactional
    public AuthSession handle(SignInCommand command) {
        if (command == null || command.email() == null || command.password() == null) {
            log.warn("Sign-in rejected: credentials are missing");
            throw new InvalidCredentialsException();
        }
        log.debug("Handling sign-in command");
        var user = findUserByEmail(command.email())
                .filter(found -> hashingService.matches(command.password(), found.getPassword().value()))
                .orElseThrow(() -> {
                    log.warn("Sign-in rejected: invalid credentials");
                    return new InvalidCredentialsException();
                });
        ensureCanSignIn(user);
        var session = startSession(user);
        log.info("Signed in account id {}", user.getId());
        return session;
    }

    /**
     * Rotates the refresh token of a session and issues a new access token.
     *
     * @param command the refresh-session command
     * @return the issued tokens
     * @throws InvalidRefreshTokenException when the token is unknown or expired
     * @throws AccountNotActiveException    when the account is no longer active
     */
    @Override
    @Transactional
    public AuthSession handle(RefreshSessionCommand command) {
        if (command == null || command.refreshToken() == null || command.refreshToken().isBlank()) {
            log.warn("Session refresh rejected: token is missing");
            throw new InvalidRefreshTokenException();
        }
        log.debug("Handling session refresh command");
        var user = userRepository.findByRefreshTokenHash(tokenService.hashRefreshToken(command.refreshToken()))
                .orElseThrow(() -> {
                    log.warn("Session refresh rejected: token not found or already rotated");
                    return new InvalidRefreshTokenException();
                });
        if (user.isRefreshTokenExpiredAt(LocalDateTime.now(clock))) {
            log.warn("Session refresh rejected: token expired for account id {}", user.getId());
            throw new InvalidRefreshTokenException();
        }
        ensureCanSignIn(user);
        var session = startSession(user);
        log.info("Refreshed session for account id {}", user.getId());
        return session;
    }

    /**
     * Closes the session bound to a refresh token. Unknown tokens are ignored.
     *
     * @param command the sign-out command
     */
    @Override
    @Transactional
    public void handle(SignOutCommand command) {
        if (command == null || command.refreshToken() == null || command.refreshToken().isBlank()) {
            return;
        }
        userRepository.findByRefreshTokenHash(tokenService.hashRefreshToken(command.refreshToken()))
                .ifPresent(user -> {
                    user.endSession();
                    userRepository.save(user);
                    log.info("Signed out account id {}", user.getId());
                });
    }

    private Optional<User> findUserByEmail(String email) {
        try {
            return userRepository.findByEmail(new EmailAddress(email));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void ensureCanSignIn(User user) {
        try {
            user.ensureCanSignIn();
        } catch (AccountNotActiveException exception) {
            log.warn("Session rejected: account id {} has status {}", user.getId(), user.getStatus());
            throw exception;
        }
    }

    private AuthSession startSession(User user) {
        var refreshToken = tokenService.generateRefreshToken();
        user.startSession(refreshToken.hash(), refreshToken.expiresAt());
        var savedUser = userRepository.save(user);
        return new AuthSession(savedUser, tokenService.generateAccessToken(savedUser),
                refreshToken.rawToken(), tokenService.getAccessTokenExpirationSeconds());
    }

    private void validatePassword(String password) {
        try {
            PasswordPolicy.validate(password);
        } catch (SignUpValidationException exception) {
            log.warn("Sign-up rejected: password does not meet the policy");
            throw exception;
        }
    }

    private void validateProfileData(SignUpCommand command) {
        if (command.fullName() == null || command.fullName().isBlank()) {
            log.warn("Sign-up rejected: full name is missing");
            throw new SignUpValidationException("fullName", SignUpValidationException.REQUIRED);
        }
        if (command.phoneNumber() == null || command.phoneNumber().isBlank()) {
            log.warn("Sign-up rejected: phone number is missing");
            throw new SignUpValidationException("phoneNumber", SignUpValidationException.REQUIRED);
        }
    }

    private EmailAddress toEmailAddress(String email) {
        try {
            return new EmailAddress(email);
        } catch (IllegalArgumentException exception) {
            log.warn("Sign-up rejected: email address is invalid");
            throw new SignUpValidationException("email", SignUpValidationException.INVALID_EMAIL);
        }
    }

    private void createProfile(User user, SignUpCommand command) {
        try {
            externalProfilesService.createProfile(user.getId(), command.toProfileData());
        } catch (RuntimeException exception) {
            log.error("Profile creation failed for account id {}: {}",
                    user.getId(), exception.getClass().getSimpleName());
            log.debug("Profile creation failure details", exception);
            throw new RegistrationUnavailableException(exception);
        }
    }

    private void sendVerificationEmail(User user, String rawToken) {
        try {
            var verificationLink = emailVerificationTokenService.buildVerificationLink(rawToken);
            verificationEmailService.sendVerificationEmail(
                    user.getEmail().email(), rawToken, verificationLink);
        } catch (RuntimeException exception) {
            log.error("Verification email delivery failed for account id {}: {}",
                    user.getId(), exception.getClass().getSimpleName());
            log.debug("Verification email delivery failure details", exception);
            throw new RegistrationUnavailableException(exception);
        }
    }
}
