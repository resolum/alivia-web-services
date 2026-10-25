package com.resolum.alivia.communications;

import com.resolum.alivia.communications.application.acl.CommunicationsContextFacadeImpl;
import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import com.resolum.alivia.communications.application.internal.templates.EmailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CommunicationsContextFacadeImplTest {

    private static final String EMAIL = "maria.lopez@example.com";
    private static final String LINK = "https://app.alivia.test/verify-email?token=raw-token&lang=es";

    private final EmailSender emailSender = mock(EmailSender.class);
    private final CommunicationsContextFacadeImpl facade =
            new CommunicationsContextFacadeImpl(new EmailTemplateRenderer(), emailSender);

    @Test
    void sendsTheVerificationEmailWithTheLinkInBothBodies() {
        facade.sendVerificationEmail(EMAIL, LINK);

        var bodies = sentBodies("Verifica tu cuenta de Alivia");
        assertTrue(bodies.get("text").contains(LINK));
        assertTrue(bodies.get("html").contains("token=raw-token&amp;lang=es"));
        assertFalse(bodies.get("html").contains("{{"));
    }

    @Test
    void derivesThePlainTextFromTheHtmlKeepingLineBreaks() {
        facade.sendVerificationEmail(EMAIL, LINK);

        var text = sentBodies("Verifica tu cuenta de Alivia").get("text");
        assertTrue(text.startsWith("Alivia\n\nConfirma tu correo\n\nGracias por registrarte"));
        assertTrue(text.contains("copia este enlace en tu navegador:\n" + LINK));
        assertFalse(text.contains("<"));
    }

    @Test
    void sendsThePasswordResetEmail() {
        facade.sendPasswordResetEmail(EMAIL, LINK);

        assertTrue(sentBodies("Restablece tu contraseña de Alivia").get("text").contains(LINK));
    }

    @Test
    void sendsTheSetPasswordEmail() {
        facade.sendSetPasswordEmail(EMAIL, LINK);

        assertTrue(sentBodies("Crea tu contraseña de Alivia").get("text").contains(LINK));
    }

    @Test
    void sendsTheWelcomeEmailWithTheNameEscaped() {
        facade.sendWelcomeEmail(EMAIL, "María <b>López</b>");

        var bodies = sentBodies("Te damos la bienvenida a Alivia");
        assertTrue(bodies.get("text").contains("María <b>López</b>"));
        assertTrue(bodies.get("html").contains("María &lt;b&gt;López&lt;/b&gt;"));
    }

    @Test
    void rejectsMissingRecipientOrLink() {
        assertThrows(IllegalArgumentException.class, () -> facade.sendVerificationEmail(" ", LINK));
        assertThrows(IllegalArgumentException.class, () -> facade.sendVerificationEmail(EMAIL, null));
        verifyNoInteractions(emailSender);
    }

    private Map<String, String> sentBodies(String subject) {
        var text = ArgumentCaptor.forClass(String.class);
        var html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(EMAIL), eq(subject), text.capture(), html.capture());
        return Map.of("text", text.getValue(), "html", html.getValue());
    }
}
