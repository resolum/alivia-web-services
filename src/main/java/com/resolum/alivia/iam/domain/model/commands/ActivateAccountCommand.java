package com.resolum.alivia.iam.domain.model.commands;

/**
 * Activates an invited account with the password its owner chose.
 *
 * @param activationToken the raw token from the invitation link
 * @param password        the new plain-text password
 */
public record ActivateAccountCommand(String activationToken, String password) {
}
