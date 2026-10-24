package com.resolum.alivia.iam.application.internal.outboundservices.notifications;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Provisional implementation of {@link VerificationEmailService} for non-development
 * profiles.
 *
 * <p>PENDING(email-provider): replace with the communications context facade or an SMTP
 * adapter. Until then, registration reports that the service is unavailable.</p>
 */
@Service
@Profile("!dev")
public class UnavailableVerificationEmailService implements VerificationEmailService {

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, because no provider is configured
     */
    @Override
    public void sendVerificationEmail(String email, String rawToken, String verificationLink) {
        throw new IllegalStateException("Verification email provider is not configured.");
    }
}
