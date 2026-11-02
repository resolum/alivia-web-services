package com.resolum.alivia.iam.domain.model.aggregates;

import com.resolum.alivia.iam.domain.exceptions.AccountNotActiveException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * User aggregate root of the IAM bounded context.
 *
 * <p>Stores the credentials, access role, lifecycle status and the pending email
 * verification token. A newly registered user starts in {@link UserStatus#PENDING_VERIFICATION}
 * with no verified email and zero failed access attempts.</p>
 *
 * <p>Value objects are persisted as primitive columns so the database schema stays simple;
 * the getters rebuild them.</p>
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_account_id", columnNames = "account_id"),
        @UniqueConstraint(
                name = "uk_users_email_verification_token_hash",
                columnNames = "email_verification_token_hash"),
        @UniqueConstraint(name = "uk_users_refresh_token_hash", columnNames = "refresh_token_hash")
})
public class User extends AuditableAbstractAggregateRoot<User> {

    // Public identifier exposed by the API; the numeric id stays internal for foreign keys.
    @Column(name = "account_id", length = 36, updatable = false)
    private String accountId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_role", nullable = false, length = 32)
    private AccessRole accessRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified;

    @Column(name = "failed_access_attempts", nullable = false)
    private Integer failedAccessAttempts;

    @Column(name = "email_verification_token_hash", length = 64)
    private String emailVerificationTokenHash;

    @Column(name = "email_verification_expires_at")
    private LocalDateTime emailVerificationExpiresAt;

    @Column(name = "email_verification_requested_at")
    private LocalDateTime emailVerificationRequestedAt;

    @Column(name = "terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

    // shortcut: one refresh token per user, so a new sign-in ends the previous session; move to a sessions table for multi-device.
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    // Profile in the Profiles context; null for roles without a profile and for accounts created before the link.
    @Column(name = "profile_id")
    private Long profileId;

    /**
     * Required by JPA.
     */
    protected User() {
    }

    /**
     * Creates a new user pending email verification.
     *
     * @param email           the normalized email address
     * @param password        the password hash
     * @param accessRole      the access role
     * @param termsAcceptedAt the moment the terms and conditions were accepted
     * @throws IllegalArgumentException if any argument is null
     */
    public User(EmailAddress email, PasswordHash password, AccessRole accessRole,
                LocalDateTime termsAcceptedAt) {
        if (email == null || password == null || accessRole == null || termsAcceptedAt == null) {
            throw new IllegalArgumentException(
                    "Email, password, access role and terms acceptance are required.");
        }
        this.accountId = UUID.randomUUID().toString();
        this.email = email.email();
        this.password = password.value();
        this.accessRole = accessRole;
        this.termsAcceptedAt = termsAcceptedAt;
        this.status = UserStatus.PENDING_VERIFICATION;
        this.emailVerified = Boolean.FALSE;
        this.failedAccessAttempts = 0;
    }

    /**
     * Replaces the pending email verification token.
     *
     * @param token       the new pending token
     * @param requestedAt the moment the token was issued
     * @throws IllegalArgumentException if any argument is null
     */
    public void requestEmailVerification(EmailVerificationToken token, LocalDateTime requestedAt) {
        if (token == null || requestedAt == null) {
            throw new IllegalArgumentException("Email verification token and request time are required.");
        }
        this.emailVerificationTokenHash = token.value();
        this.emailVerificationExpiresAt = token.expiresAt();
        this.emailVerificationRequestedAt = requestedAt;
    }

    /**
     * Returns the seconds left until a new verification email may be requested.
     *
     * @param now      the moment to compare against
     * @param cooldown the minimum time between two requests
     * @return the remaining wait rounded up to whole seconds, or {@code 0} when allowed
     */
    public long secondsUntilVerificationResend(LocalDateTime now, Duration cooldown) {
        if (emailVerificationRequestedAt == null) {
            return 0;
        }
        var wait = Duration.between(now.atOffset(ZoneOffset.UTC),
                emailVerificationRequestedAt.plus(cooldown).atOffset(ZoneOffset.UTC));
        return wait.isPositive() ? wait.plusNanos(999_999_999).toSeconds() : 0;
    }

    /**
     * Activates the account after a successful email verification and consumes the
     * pending token.
     *
     * @throws AccountNotPendingVerificationException if the account is not pending verification
     */
    public void verifyEmail() {
        if (status != UserStatus.PENDING_VERIFICATION) {
            throw new AccountNotPendingVerificationException();
        }
        this.status = UserStatus.ACTIVE;
        this.emailVerified = Boolean.TRUE;
        this.emailVerificationTokenHash = null;
        this.emailVerificationExpiresAt = null;
    }

    /**
     * Activates an invited account: its owner chose the password and, by using the link sent to
     * the email address, also verified it.
     *
     * @param password    the hash of the chosen password
     * @param activatedAt the moment of the activation, also taken as the terms acceptance
     * @throws AccountNotPendingVerificationException if the account was already activated
     */
    public void activate(PasswordHash password, LocalDateTime activatedAt) {
        if (password == null || activatedAt == null) {
            throw new IllegalArgumentException("Password and activation time are required.");
        }
        verifyEmail();
        this.password = password.value();
        this.termsAcceptedAt = activatedAt;
        this.failedAccessAttempts = 0;
    }

    /**
     * Checks that the account may open or renew a session. Accounts pending email
     * verification may sign in so the user is not blocked while offline.
     *
     * @throws AccountNotActiveException if the account is blocked or inactive
     */
    public void ensureCanSignIn() {
        if (status != UserStatus.ACTIVE && status != UserStatus.PENDING_VERIFICATION) {
            throw new AccountNotActiveException(status);
        }
    }

    /**
     * Stores the hash of a new refresh token, replacing the previous one.
     *
     * @param tokenHash the SHA-256 hash of the refresh token
     * @param expiresAt the expiration moment
     * @throws IllegalArgumentException if any argument is null
     */
    public void startSession(String tokenHash, LocalDateTime expiresAt) {
        if (tokenHash == null || expiresAt == null) {
            throw new IllegalArgumentException("Refresh token hash and expiration are required.");
        }
        this.refreshTokenHash = tokenHash;
        this.refreshTokenExpiresAt = expiresAt;
    }

    /**
     * Discards the stored refresh token.
     */
    public void endSession() {
        this.refreshTokenHash = null;
        this.refreshTokenExpiresAt = null;
    }

    /**
     * Indicates whether the stored refresh token is missing or expired at the given moment.
     *
     * @param now the moment to compare against
     * @return {@code true} when no valid refresh token exists
     */
    public boolean isRefreshTokenExpiredAt(LocalDateTime now) {
        return refreshTokenExpiresAt == null || !now.isBefore(refreshTokenExpiresAt);
    }

    /**
     * Returns the public account identifier.
     *
     * @return the account identifier as a UUID string
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Links the user to the profile created for it in the Profiles context.
     *
     * @param profileId the profile identifier
     * @throws IllegalArgumentException if the identifier is null or not positive
     */
    public void assignProfile(Long profileId) {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile identifier must be positive.");
        }
        this.profileId = profileId;
    }

    /**
     * Returns the profile identifier in the Profiles context.
     *
     * @return the profile identifier, or {@code null} when the user has no profile
     */
    public Long getProfileId() {
        return profileId;
    }

    /**
     * Returns the normalized email address.
     *
     * @return the email address
     */
    public EmailAddress getEmail() {
        return new EmailAddress(email);
    }

    /**
     * Returns the password hash.
     *
     * @return the password hash
     */
    public PasswordHash getPassword() {
        return new PasswordHash(password);
    }

    /**
     * Returns the access role.
     *
     * @return the access role
     */
    public AccessRole getAccessRole() {
        return accessRole;
    }

    /**
     * Returns the lifecycle status.
     *
     * @return the user status
     */
    public UserStatus getStatus() {
        return status;
    }

    /**
     * Indicates whether the email address is verified.
     *
     * @return {@code true} when the email is verified
     */
    public Boolean getEmailVerified() {
        return emailVerified;
    }

    /**
     * Returns the number of failed access attempts.
     *
     * @return the failed access attempts
     */
    public Integer getFailedAccessAttempts() {
        return failedAccessAttempts;
    }

    /**
     * Returns the pending email verification token.
     *
     * @return the pending token, or {@code null} when none exists
     */
    public EmailVerificationToken getPendingEmailVerificationToken() {
        if (emailVerificationTokenHash == null) {
            return null;
        }
        return new EmailVerificationToken(emailVerificationTokenHash, emailVerificationExpiresAt);
    }

    /**
     * Returns the moment the terms and conditions were accepted.
     *
     * @return the acceptance moment
     */
    public LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }
}
