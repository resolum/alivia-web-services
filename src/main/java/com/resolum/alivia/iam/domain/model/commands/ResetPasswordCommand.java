package com.resolum.alivia.iam.domain.model.commands;

/**
 * Sets a new password with the 6-digit code sent to the account email.
 *
 * @param email    the raw email address
 * @param code     the 6-digit code from the email
 * @param password the new plain-text password
 */
public record ResetPasswordCommand(String email, String code, String password) {
}
