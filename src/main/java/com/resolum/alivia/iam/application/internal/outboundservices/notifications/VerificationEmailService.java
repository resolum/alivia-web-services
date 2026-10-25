package com.resolum.alivia.iam.application.internal.outboundservices.notifications;

/**
 * Outbound port for sending email verification messages.
 *
 * <p>Implementations must not log the recipient address or the verification token
 * outside the development profile.</p>
 */
public interface VerificationEmailService {

    /**
     * Sends the verification link to the account email address.
     *
     * @param email            the recipient email address
     * @param rawToken         the plain-text verification token
     * @param verificationLink the link that contains the plain-text verification token
     */
    void sendVerificationEmail(String email, String rawToken, String verificationLink);
}
