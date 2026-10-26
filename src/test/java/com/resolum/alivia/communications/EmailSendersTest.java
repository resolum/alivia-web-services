package com.resolum.alivia.communications;

import com.resolum.alivia.communications.application.internal.outboundservices.email.LoggingEmailSender;
import com.resolum.alivia.communications.application.internal.outboundservices.email.UnavailableEmailSender;
import com.resolum.alivia.communications.infrastructure.email.smtp.SmtpEmailSender;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailSendersTest {

    private static final String FROM = "no-reply@alivia.test";
    private static final String TO = "maria.lopez@example.com";
    private static final String SUBJECT = "Verifica tu cuenta de Alivia";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MimeMessage mimeMessage =
            new MimeMessage(Session.getInstance(new Properties()));

    @Test
    void smtpSenderSendsPlainTextAndHtmlBodies() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        new SmtpEmailSender(mailSender, FROM).send(TO, SUBJECT, "plain body", "<p>html body</p>");

        verify(mailSender).send(mimeMessage);
        assertEquals(SUBJECT, mimeMessage.getSubject());
        assertEquals(FROM, mimeMessage.getFrom()[0].toString());
        assertEquals(TO, mimeMessage.getAllRecipients()[0].toString());
        assertEquals(List.of("plain body", "<p>html body</p>"), textBodies(mimeMessage));
    }

    @Test
    void smtpSenderWrapsDeliveryFailuresAsIllegalState() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        var sender = new SmtpEmailSender(mailSender, FROM);

        var exception = assertThrows(IllegalStateException.class,
                () -> sender.send(TO, SUBJECT, "plain", "<p>html</p>"));

        assertEquals("Email could not be delivered.", exception.getMessage());
        assertTrue(exception.getCause() instanceof MailSendException);
    }

    @Test
    void smtpSenderRejectsAMissingSenderAddress() {
        assertThrows(IllegalArgumentException.class, () -> new SmtpEmailSender(mailSender, "  "));
    }

    @Test
    void loggingSenderLogsWithoutException() {
        assertDoesNotThrow(() -> new LoggingEmailSender().send(TO, SUBJECT, "plain", "<p>html</p>"));
    }

    @Test
    void unavailableSenderAlwaysFails() {
        assertThrows(IllegalStateException.class,
                () -> new UnavailableEmailSender().send(TO, SUBJECT, "plain", "<p>html</p>"));
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
