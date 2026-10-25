package com.resolum.alivia.iam.domain.model.aggregates;

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

import java.time.LocalDateTime;

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
        @UniqueConstraint(
                name = "uk_users_email_verification_token_hash",
                columnNames = "email_verification_token_hash")
})
public class User extends AuditableAbstractAggregateRoot<User> {

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

    @Column(name = "terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

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
     * @param token the new pending token
     * @throws IllegalArgumentException if the token is null
     */
    public void requestEmailVerification(EmailVerificationToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Email verification token is required.");
        }
        this.emailVerificationTokenHash = token.value();
        this.emailVerificationExpiresAt = token.expiresAt();
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
