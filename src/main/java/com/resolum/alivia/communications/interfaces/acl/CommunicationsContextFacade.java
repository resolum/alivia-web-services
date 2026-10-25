package com.resolum.alivia.communications.interfaces.acl;

/**
 * Facade for other bounded contexts (e.g. IAM) that need to send notifications.
 *
 * <p>It exposes primitive types only, so consumers never depend on Communications
 * internals such as templates or delivery providers.</p>
 */
public interface CommunicationsContextFacade {

    /**
     * Sends the account verification email.
     *
     * @param email            the recipient email address
     * @param verificationLink the link that verifies the account
     * @throws IllegalArgumentException when the email or the link is missing
     * @throws IllegalStateException    when the email cannot be delivered
     */
    void sendVerificationEmail(String email, String verificationLink);

    /**
     * Sends the password reset email.
     *
     * @param email     the recipient email address
     * @param resetLink the link that opens the password reset form
     * @throws IllegalArgumentException when the email or the link is missing
     * @throws IllegalStateException    when the email cannot be delivered
     */
    void sendPasswordResetEmail(String email, String resetLink);

    /**
     * Sends the email that lets an account created by someone else set its first password.
     *
     * @param email           the recipient email address
     * @param setPasswordLink the link that opens the set password form
     * @throws IllegalArgumentException when the email or the link is missing
     * @throws IllegalStateException    when the email cannot be delivered
     */
    void sendSetPasswordEmail(String email, String setPasswordLink);

    /**
     * Sends the welcome email once the account is active.
     *
     * @param email    the recipient email address
     * @param fullName the name used to greet the recipient
     * @throws IllegalArgumentException when the email or the name is missing
     * @throws IllegalStateException    when the email cannot be delivered
     */
    void sendWelcomeEmail(String email, String fullName);
}
