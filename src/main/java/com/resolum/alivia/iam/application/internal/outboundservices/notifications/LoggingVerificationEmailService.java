package com.resolum.alivia.iam.application.internal.outboundservices.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local development implementation of {@link VerificationEmailService}.
 *
 * <p>Writes the verification link to the log so it can be used locally. It is the default
 * provider ({@code alivia.iam.email-verification.provider=log}) and is never active in the
 * {@code prod} profile. It never logs the recipient address.</p>
 */
@Slf4j
@Service
@Profile("!prod")
@ConditionalOnProperty(prefix = "alivia.iam.email-verification", name = "provider",
        havingValue = "log", matchIfMissing = true)
public class LoggingVerificationEmailService implements VerificationEmailService {

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVerificationEmail(String email, String rawToken, String verificationLink) {
        log.info("Email verification link: {}", verificationLink);
    }
}
