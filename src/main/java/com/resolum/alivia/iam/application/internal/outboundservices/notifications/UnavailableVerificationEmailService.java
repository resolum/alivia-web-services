package com.resolum.alivia.iam.application.internal.outboundservices.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Fail-closed implementation of {@link VerificationEmailService} for production when no
 * real provider has been configured.
 *
 * <p>It is the {@code prod} default ({@code alivia.iam.email-verification.provider=unavailable})
 * so a deployment never logs verification links. Registration reports that the service is
 * unavailable until {@code IAM_EMAIL_PROVIDER=smtp} and the {@code spring.mail.*} settings
 * are supplied.</p>
 */
@Service("verificationEmailService")
@Profile("prod")
@ConditionalOnProperty(prefix = "alivia.iam.email-verification", name = "provider",
        havingValue = "unavailable", matchIfMissing = true)
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
