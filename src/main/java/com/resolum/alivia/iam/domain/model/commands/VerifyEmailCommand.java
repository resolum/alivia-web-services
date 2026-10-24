package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that requests the verification of an account email address.
 *
 * @param accountId         the identifier of the account that owns the token
 * @param verificationToken the plain-text verification token received by the user
 */
public record VerifyEmailCommand(Long accountId, String verificationToken) {
}
