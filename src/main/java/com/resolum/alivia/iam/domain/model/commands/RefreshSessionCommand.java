package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that renews a session with its refresh token.
 *
 * @param refreshToken the plain-text refresh token
 */
public record RefreshSessionCommand(String refreshToken) {
}
