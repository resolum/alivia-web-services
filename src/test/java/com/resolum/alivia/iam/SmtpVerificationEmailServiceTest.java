package com.resolum.alivia.iam;

import com.resolum.alivia.iam.infrastructure.notifications.smtp.SmtpVerificationEmailService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpVerificationEmailServiceTest {

    private static final String FROM = "no-reply@alivia.test";
    private static final String SUBJECT = "Verifica tu cuenta de Alivia";
    private static final String EMAIL = "maria.lopez@example.com";
    private static final String RAW_TOKEN = "raw-token";
    private static final String VERIFICATION_LINK =
            "https://app.alivia.test/verify-email?token=raw-token&lang=es";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MimeMessage mimeMessage =
            new MimeMessage(Session.getInstance(new Properties()));

    @Test
    void sendsPlainTextAndHtmlBodiesWithTheVerificationLink() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        var service = new SmtpVerificationEmailService(mailSender, FROM, SUBJECT);

        service.sendVerificationEmail(EMAIL, RAW_TOKEN, VERIFICATION_LINK);

        verify(mailSender).send(mimeMessage);
        assertEquals(SUBJECT, mimeMessage.getSubject());
        assertEquals(FROM, mimeMessage.getFrom()[0].toString());
        assertEquals(EMAIL, mimeMessage.getAllRecipients()[0].toString());

        var bodies = textBodies(mimeMessage);
        assertEquals(2, bodies.size());
        assertTrue(bodies.stream().anyMatch(body -> body.contains(VERIFICATION_LINK)));
        assertTrue(bodies.stream()
                .anyMatch(body -> body.contains("token=raw-token&amp;lang=es")));
    }

    @Test
    void wrapsDeliveryFailuresAsIllegalState() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        var service = new SmtpVerificationEmailService(mailSender, FROM, SUBJECT);

        var exception = assertThrows(IllegalStateException.class,
                () -> service.sendVerificationEmail(EMAIL, RAW_TOKEN, VERIFICATION_LINK));

        assertEquals("Verification email could not be delivered.", exception.getMessage());
        assertTrue(exception.getCause() instanceof MailSendException);
    }

    @Test
    void rejectsAMissingSenderAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmtpVerificationEmailService(mailSender, "  ", SUBJECT));
    }

    @Test
    void fallsBackToTheDefaultSubject() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        var service = new SmtpVerificationEmailService(mailSender, FROM, " ");

        service.sendVerificationEmail(EMAIL, RAW_TOKEN, VERIFICATION_LINK);

        assertEquals(SUBJECT, mimeMessage.getSubject());
    }

    private static List<String> textBodies(Part part) throws Exception {
        var bodies = new ArrayList<String>();
        var content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                bodies.addAll(textBodies(multipart.getBodyPart(index)));
            }
        } else if (content instanceof String text) {
            bodies.add(text);
        }
        return bodies;
    }
}
