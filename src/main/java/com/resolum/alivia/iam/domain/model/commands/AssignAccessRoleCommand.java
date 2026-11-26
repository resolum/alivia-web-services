package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

/**
 * Command that grants a role to a user; the user keeps the roles it had.
 *
 * @param userId     the internal user id
 * @param accessRole the role to grant
 */
public record AssignAccessRoleCommand(Long userId, AccessRole accessRole) {
}
