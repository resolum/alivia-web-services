package com.resolum.alivia.communications.interfaces.acl;

import java.util.Map;

/**
 * Facade for other bounded contexts (e.g. IAM) that need to send notifications.
 *
 * <p>It exposes primitive types only, so consumers never depend on Communications
 * internals such as templates or delivery providers.</p>
 */
public interface CommunicationsContextFacade {

    /**
     * Sends an email built from one of the Communications templates.
     *
     * <p>Templates: {@code verify-email}, {@code reset-password} and {@code set-password}
     * take a {@code link}; {@code welcome} takes a {@code name}.</p>
     *
     * @param email     the recipient email address
     * @param template  the template name, e.g. {@code verify-email}
     * @param variables the placeholder values of the template
     * @throws IllegalArgumentException when the email, the template or a value is missing or unknown
     * @throws IllegalStateException    when the email cannot be delivered
     */
    void sendEmail(String email, String template, Map<String, String> variables);
}
