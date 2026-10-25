package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

/**
 * Command that requests the registration of a new account with a specific access role.
 *
 * @param email         the account email address
 * @param password      the plain-text password, used only to produce its hash
 * @param fullName      the full name forwarded to the Profiles context
 * @param phoneNumber   the phone number forwarded to the Profiles context
 * @param termsAccepted whether the user accepted the terms and conditions
 * @param role          the access role assigned to the user
 * @param businessId    the optional business identifier forwarded to Profiles
 * @param familiarId    the optional familiar identifier forwarded to Profiles
 */
public record SignUpCommand(
        String email,
        String password,
        String fullName,
        String phoneNumber,
        boolean termsAccepted,
        AccessRole role,
        Long businessId,
        Long familiarId
) {
    public SignUpCommand(
            String email,
            String password,
            String fullName,
            String phoneNumber,
            boolean termsAccepted
    ) {
        this(email, password, fullName, phoneNumber, termsAccepted, AccessRole.CAREGIVER, null, null);
    }

    public SignUpCommand(
            String email,
            String password,
            String fullName,
            String phoneNumber,
            boolean termsAccepted,
            AccessRole role
    ) {
        this(email, password, fullName, phoneNumber, termsAccepted, role, null, null);
    }
}
