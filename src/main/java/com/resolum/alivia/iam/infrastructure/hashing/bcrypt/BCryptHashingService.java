package com.resolum.alivia.iam.infrastructure.hashing.bcrypt;

import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;

/**
 * BCrypt-specific hashing service.
 *
 * <p>It exists so the concrete encoder can be replaced without changing the application
 * port, mirroring the reference IAM structure.</p>
 */
public interface BCryptHashingService extends HashingService {
}
