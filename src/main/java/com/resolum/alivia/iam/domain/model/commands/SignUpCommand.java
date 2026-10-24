package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that requests the registration of a new account.
 *
 * @param email         the account email address
 * @param password      the plain-text password, used only to produce its hash
 * @param fullName      the full name forwarded to the Profiles context
 * @param phoneNumber   the phone number forwarded to the Profiles context
 * @param termsAccepted whether the user accepted the terms and conditions
 */
public record SignUpCommand(
        String email,
        String password,
        String fullName,
        String phoneNumber,
        boolean termsAccepted
) {
}
