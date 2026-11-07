package com.resolum.alivia.iam.application.internal.commandservices;

import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.domain.exceptions.AccountNotFoundException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.ChangePasswordCommand;
import com.resolum.alivia.iam.domain.model.commands.ForgotPasswordCommand;
import com.resolum.alivia.iam.domain.model.commands.ResetPasswordCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.PasswordPolicy;
import com.resolum.alivia.iam.domain.services.PasswordResetService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Forgot-password flow with a 6-digit code sent by email. The request never tells whether the
 * email has an account, so unknown, inactive or too frequent requests end silently. A code
 * allows {@link User#MAX_CODE_ATTEMPTS} wrong guesses before a new one must be requested.
 */
@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {
    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final EmailVerificationTokenService tokenService;
    private final ExternalCommunicationsService externalCommunicationsService;
    private final Clock clock;
    private final int expirationMinutes;
    private final Duration cooldown;

    public PasswordResetServiceImpl(UserRepository userRepository, HashingService hashingService,
                                    EmailVerificationTokenService tokenService,
                                    ExternalCommunicationsService externalCommunicationsService, Clock clock,
                                    @Value("${alivia.iam.password-reset.expiration-minutes:60}") int expirationMinutes,
                                    @Value("${alivia.iam.email-verification.resend-cooldown-seconds:60}") long cooldownSeconds) {
        if (expirationMinutes <= 0) throw new IllegalArgumentException("Password reset code expiration must be positive.");
        if (cooldownSeconds < 0) throw new IllegalArgumentException("Password reset cooldown cannot be negative.");
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.externalCommunicationsService = externalCommunicationsService;
        this.clock = clock;
        this.expirationMinutes = expirationMinutes;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    @Override
    @Transactional
    public void handle(ForgotPasswordCommand command) {
        var user = findByEmail(command == null ? null : command.email());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset ignored: no active account for the given email");
            return;
        }
        var now = LocalDateTime.now(clock);
        if (!user.canRequestPasswordResetAt(now, cooldown)) {
            log.warn("Password reset ignored: account id {} asked again within the cooldown", user.getId());
            return;
        }
        var generated = tokenService.generateCode(user.getAccountId());
        user.requestPasswordReset(new EmailVerificationToken(generated.token().value(),
                now.plusMinutes(expirationMinutes)), now);
        var savedUser = userRepository.save(user);
        try {
            externalCommunicationsService.sendPasswordResetEmail(savedUser.getEmail().email(), generated.rawToken());
            log.info("Sent password reset code for account id {}", savedUser.getId());
        } catch (RuntimeException exception) {
            // Failing the request would reveal that the email has an account.
            log.error("Password reset email delivery failed for account id {}: {}",
                    savedUser.getId(), exception.getClass().getSimpleName());
        }
    }

    @Override
    // The wrong-code counter must survive the rejection.
    @Transactional(noRollbackFor = InvalidVerificationTokenException.class)
    public void handle(ResetPasswordCommand command) {
        var user = command == null ? null : findByEmail(command.email());
        var pending = user == null ? null : user.getPendingPasswordResetToken();
        if (pending == null || !user.hasCodeAttemptsLeft()) throw new InvalidVerificationTokenException();
        if (!tokenService.matchesCode(user.getAccountId(), command.code(), pending.value())) {
            user.registerFailedCodeAttempt();
            userRepository.save(user);
            log.warn("Password reset rejected: wrong code for account id {}", user.getId());
            throw new InvalidVerificationTokenException();
        }
        if (pending.isExpiredAt(LocalDateTime.now(clock))) throw new VerificationTokenExpiredException();
        PasswordPolicy.validate(command.password());
        user.resetPassword(new PasswordHash(hashingService.encode(command.password())));
        userRepository.save(user);
        log.info("Reset password of account id {}", user.getId());
    }

    @Override
    @Transactional
    public void handle(ChangePasswordCommand command) {
        if (command == null || command.userId() == null) throw new IllegalArgumentException("Authenticated user is required.");
        var user = userRepository.findById(command.userId()).orElseThrow(AccountNotFoundException::new);
        user.ensureCanSignIn();
        if (command.currentPassword() == null
                || !hashingService.matches(command.currentPassword(), user.getPassword().value())) {
            log.warn("Password change rejected: wrong current password for account id {}", user.getId());
            throw new SignUpValidationException("currentPassword", SignUpValidationException.INCORRECT);
        }
        PasswordPolicy.validate(command.newPassword());
        user.changePassword(new PasswordHash(hashingService.encode(command.newPassword())));
        userRepository.save(user);
        log.info("Changed password of account id {}", user.getId());
    }

    /** Locks the row so parallel guesses cannot skip the wrong-code limit. */
    private User findByEmail(String email) {
        try {
            return userRepository.findByEmailForUpdate(new EmailAddress(email)).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
