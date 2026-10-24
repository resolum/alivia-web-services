package com.resolum.alivia.iam.application.internal.outboundservices.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development implementation of {@link VerificationEmailService}.
 *
 * <p>Writes the verification link to the log so it can be used locally. It is active
 * only in the {@code dev} profile and never logs the recipient address.</p>
 */
@Slf4j
@Service
@Profile("dev")
public class LoggingVerificationEmailService implements VerificationEmailService {

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVerificationEmail(String email, String rawToken, String verificationLink) {
        log.info("Email verification link: {}", verificationLink);
    }
}
