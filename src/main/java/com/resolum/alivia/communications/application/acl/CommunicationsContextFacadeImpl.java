package com.resolum.alivia.communications.application.acl;

import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import com.resolum.alivia.communications.application.internal.templates.EmailTemplateRenderer;
import com.resolum.alivia.communications.interfaces.acl.CommunicationsContextFacade;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementation of the Communications anti-corruption layer facade.
 *
 * <p>Renders the email templates under {@code templates/email/} and hands the result to
 * the configured {@link EmailSender}.</p>
 */
@Service
public class CommunicationsContextFacadeImpl implements CommunicationsContextFacade {

    private final EmailTemplateRenderer templateRenderer;
    private final EmailSender emailSender;

    /**
     * Creates the facade.
     *
     * @param templateRenderer the email template renderer
     * @param emailSender      the email delivery port
     */
    public CommunicationsContextFacadeImpl(EmailTemplateRenderer templateRenderer,
            @Qualifier("emailSender") EmailSender emailSender) {
        this.templateRenderer = templateRenderer;
        this.emailSender = emailSender;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVerificationEmail(String email, String verificationLink) {
        send(email, "Verifica tu cuenta de Alivia", "verify-email",
                "link", requireText(verificationLink, "Verification link"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendPasswordResetEmail(String email, String resetLink) {
        send(email, "Restablece tu contraseña de Alivia", "reset-password",
                "link", requireText(resetLink, "Reset link"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendSetPasswordEmail(String email, String setPasswordLink) {
        send(email, "Crea tu contraseña de Alivia", "set-password",
                "link", requireText(setPasswordLink, "Set password link"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendWelcomeEmail(String email, String fullName) {
        send(email, "Te damos la bienvenida a Alivia", "welcome",
                "name", requireText(fullName, "Full name"));
    }

    private void send(String email, String subject, String template, String variable, String value) {
        var recipient = requireText(email, "Recipient email");
        var html = templateRenderer.renderHtml(template, Map.of(variable, value));
        emailSender.send(recipient, subject, templateRenderer.toPlainText(html), html);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim();
    }
}
