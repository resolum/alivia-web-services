package com.resolum.alivia.iam.domain.model.commands;

/**
 * Changes the password of the signed-in user.
 *
 * @param userId          the internal id of the authenticated user
 * @param currentPassword the password in use, to confirm it is the owner
 * @param newPassword     the new plain-text password
 */
public record ChangePasswordCommand(Long userId, String currentPassword, String newPassword) {
}
