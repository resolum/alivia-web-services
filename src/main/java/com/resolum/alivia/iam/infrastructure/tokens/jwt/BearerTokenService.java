package com.resolum.alivia.iam.infrastructure.tokens.jwt;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;

/**
 * Marker interface for token service implementations of the IAM context.
 *
 * <p>TS-01 only issues email verification tokens, so the contract currently extends
 * {@link EmailVerificationTokenService}. PENDING(auth): when login is implemented this
 * interface will also extend the bearer-token contract and the implementation will emit
 * signed JWTs.</p>
 */
public interface BearerTokenService extends EmailVerificationTokenService {
}
