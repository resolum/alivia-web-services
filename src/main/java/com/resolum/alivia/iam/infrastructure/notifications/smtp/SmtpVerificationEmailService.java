package com.resolum.alivia.iam.infrastructure.notifications.smtp;

import com.resolum.alivia.iam.application.internal.outboundservices.notifications.VerificationEmailService;
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
 * SMTP implementation of {@link VerificationEmailService}.
 *
 * <p>Active only when {@code alivia.iam.email-verification.provider=smtp}; the SMTP
 * connection itself is configured through the standard {@code spring.mail.*} properties.
 * The recipient address and the verification token are never logged.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "alivia.iam.email-verification", name = "provider",
        havingValue = "smtp")
public class SmtpVerificationEmailService implements VerificationEmailService {

    private static final String DEFAULT_SUBJECT = "Verifica tu cuenta de Alivia";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String subject;

    /**
     * Creates the SMTP email adapter.
     *
     * @param mailSender  the configured JavaMail sender
     * @param fromAddress the sender address shown to the recipient
     * @param subject     the verification email subject
     */
    public SmtpVerificationEmailService(
            JavaMailSender mailSender,
            @Value("${alivia.iam.email-verification.from}") String fromAddress,
            @Value("${alivia.iam.email-verification.subject:" + DEFAULT_SUBJECT + "}") String subject) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "alivia.iam.email-verification.from is required when the email provider is smtp.");
        }
        this.mailSender = mailSender;
        this.fromAddress = fromAddress.trim();
        this.subject = subject == null || subject.isBlank() ? DEFAULT_SUBJECT : subject.trim();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when the message cannot be built or handed to the SMTP server
     */
    @Override
    public void sendVerificationEmail(String email, String rawToken, String verificationLink) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(asPlainText(verificationLink), asHtml(verificationLink));
            mailSender.send(message);
            log.info("Verification email handed to the SMTP provider");
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Verification email could not be delivered.", exception);
        }
    }

    private static String asPlainText(String verificationLink) {
        return """
                Hola,

                Gracias por registrarte en Alivia. Confirma tu correo abriendo este enlace:
                %s

                Si no creaste esta cuenta, ignora este mensaje.
                """.formatted(verificationLink);
    }

    private static String asHtml(String verificationLink) {
        var escapedLink = verificationLink
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
        return """
                <p>Hola,</p>
                <p>Gracias por registrarte en Alivia. Confirma tu correo con el siguiente enlace:</p>
                <p><a href="%s">Verificar mi correo</a></p>
                <p>Si no creaste esta cuenta, ignora este mensaje.</p>
                """.formatted(escapedLink);
    }
}
