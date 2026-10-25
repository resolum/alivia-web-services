package com.resolum.alivia.communications.application.internal.outboundservices.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Fail-closed implementation of {@link EmailSender} for production when no real provider
 * has been configured.
 *
 * <p>It is the {@code prod} default ({@code alivia.communications.email.provider=unavailable})
 * so a deployment never logs single-use links. Sending fails until
 * {@code IAM_EMAIL_PROVIDER=smtp} and the {@code spring.mail.*} settings are supplied.</p>
 */
@Service("emailSender")
@Profile("prod")
@ConditionalOnProperty(prefix = "alivia.communications.email", name = "provider",
        havingValue = "unavailable", matchIfMissing = true)
public class UnavailableEmailSender implements EmailSender {

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, because no provider is configured
     */
    @Override
    public void send(String to, String subject, String plainText, String html) {
        throw new IllegalStateException("Email provider is not configured.");
    }
}
