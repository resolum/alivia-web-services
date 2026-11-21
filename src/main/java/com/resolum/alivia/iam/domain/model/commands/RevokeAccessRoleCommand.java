package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

/**
 * Command that revokes one role of a user; the other roles stay.
 *
 * @param userId     the internal user id
 * @param accessRole the role to revoke
 */
public record RevokeAccessRoleCommand(Long userId, AccessRole accessRole) {
}
