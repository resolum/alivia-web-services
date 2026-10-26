package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that closes the session bound to a refresh token.
 *
 * @param refreshToken the plain-text refresh token
 */
public record SignOutCommand(String refreshToken) {
}
