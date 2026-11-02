package com.resolum.alivia.communications.application.acl;

import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import com.resolum.alivia.communications.application.internal.templates.EmailTemplateRenderer;
import com.resolum.alivia.communications.interfaces.acl.CommunicationsContextFacade;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Communications anti-corruption layer facade.
 *
 * <p>Renders the email templates under {@code templates/email/} and hands the result to
 * the configured {@link EmailSender}.</p>
 */
@Service
public class CommunicationsContextFacadeImpl implements CommunicationsContextFacade {

    private static final Map<String, String> SUBJECTS = Map.of(
            "verify-email", "Verifica tu cuenta de Alivia",
            "reset-password", "Restablece tu contraseña de Alivia",
            "set-password", "Crea tu contraseña de Alivia",
            "welcome", "Te damos la bienvenida a Alivia",
            "account-invitation", "Bienvenido al equipo de Alivia · Activa tu cuenta");

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
    public void sendEmail(String email, String template, Map<String, String> variables) {
        var recipient = requireText(email, "Recipient email");
        var subject = SUBJECTS.get(requireText(template, "Template"));
        if (subject == null) {
            throw new IllegalArgumentException("Unknown email template: " + template);
        }
        var values = new HashMap<String, String>();
        variables.forEach((name, value) -> values.put(name, requireText(value, name)));
        var html = templateRenderer.renderHtml(template.trim(), values);
        emailSender.send(recipient, subject, templateRenderer.toPlainText(html), html);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim();
    }
}
