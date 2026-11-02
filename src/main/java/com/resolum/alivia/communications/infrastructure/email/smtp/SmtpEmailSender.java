package com.resolum.alivia.communications.infrastructure.email.smtp;

import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * SMTP implementation of {@link EmailSender}.
 *
 * <p>Active only when {@code alivia.communications.email.provider=smtp}; the SMTP
 * connection itself is configured through the standard {@code spring.mail.*} properties.
 * The recipient address and the message body are never logged.</p>
 */
@Slf4j
@Service("emailSender")
@ConditionalOnProperty(prefix = "alivia.communications.email", name = "provider",
        havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    /**
     * Creates the SMTP email adapter.
     *
     * @param mailSender  the configured JavaMail sender
     * @param fromAddress the sender address shown to the recipient
     */
    public SmtpEmailSender(JavaMailSender mailSender,
            @Value("${alivia.communications.email.from}") String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "alivia.communications.email.from is required when the email provider is smtp.");
        }
        this.mailSender = mailSender;
        this.fromAddress = fromAddress.trim();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when the message cannot be built or handed to the SMTP server
     */
    @Override
    public void send(String to, String subject, String plainText, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(message);
            log.info("Email handed to the SMTP provider");
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Email could not be delivered.", exception);
        }
    }
}
