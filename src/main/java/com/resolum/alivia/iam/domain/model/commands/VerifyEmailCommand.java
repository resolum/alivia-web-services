package com.resolum.alivia.iam.domain.model.commands;

/**
 * Verifies the email of an account with the 6-digit code sent to it.
 *
 * @param email the raw email address
 * @param code  the 6-digit code from the email
 */
public record VerifyEmailCommand(String email, String code) {
}
