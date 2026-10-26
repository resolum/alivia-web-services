package com.resolum.alivia.iam.infrastructure.tokens.jwt;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;

/**
 * Marker interface for token service implementations of the IAM context.
 *
 * <p>One implementation issues email verification tokens, signed JWT access tokens and
 * opaque refresh tokens.</p>
 */
public interface BearerTokenService extends EmailVerificationTokenService, TokenService {
}
