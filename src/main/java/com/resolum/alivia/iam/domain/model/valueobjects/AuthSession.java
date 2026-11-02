package com.resolum.alivia.iam.domain.model.valueobjects;

import com.resolum.alivia.iam.domain.model.aggregates.User;

/**
 * Tokens issued for an authenticated user.
 *
 * @param user         the authenticated user
 * @param accessToken  the signed access token
 * @param refreshToken the plain-text refresh token, only its hash is stored
 * @param expiresIn    seconds until the access token expires
 */
public record AuthSession(User user, String accessToken, String refreshToken, long expiresIn) {
}
