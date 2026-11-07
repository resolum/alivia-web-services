package com.resolum.alivia.iam.domain.model.commands;

/**
 * Asks for a password reset link sent to the account email.
 *
 * @param email the raw email address
 */
public record ForgotPasswordCommand(String email) {
}
