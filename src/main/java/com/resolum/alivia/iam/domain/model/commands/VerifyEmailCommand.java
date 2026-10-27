package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that requests the verification of an account email address.
 *
 * @param verificationToken the plain-text verification token received by the user
 */
public record VerifyEmailCommand(String verificationToken) {
}
