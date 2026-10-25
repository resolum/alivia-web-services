package com.resolum.alivia.communications.application.internal.outboundservices.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local development implementation of {@link EmailSender}.
 *
 * <p>Writes the plain-text body to the log so links can be used locally. It is the default
 * provider ({@code alivia.communications.email.provider=log}) and is never active in the
 * {@code prod} profile. It never logs the recipient address.</p>
 */
@Slf4j
@Primary
@Service("emailSender")
@Profile("!prod")
@ConditionalOnProperty(prefix = "alivia.communications.email", name = "provider",
        havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(String to, String subject, String plainText, String html) {
        log.info("Email \"{}\":\n{}", subject, plainText);
    }
}
