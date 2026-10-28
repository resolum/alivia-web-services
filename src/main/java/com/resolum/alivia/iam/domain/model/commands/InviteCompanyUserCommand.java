package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

/**
 * Creates a staff account without a password: the person receives an invitation link and creates it.
 *
 * @param email     the corporate email address, used to sign in
 * @param fullName  the name shown in the invitation
 * @param roleLabel the role shown in the invitation (for example "Soporte técnico")
 * @param role      the access role of the account
 * @param profileId the staff profile already created by the inviting context
 */
public record InviteCompanyUserCommand(String email, String fullName, String roleLabel, AccessRole role,
                                       Long profileId) {
}
