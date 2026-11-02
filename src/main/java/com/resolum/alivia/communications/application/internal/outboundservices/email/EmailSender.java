package com.resolum.alivia.communications.application.internal.outboundservices.email;

/**
 * Outbound port for delivering emails.
 *
 * <p>Implementations must not log the recipient address, and must not log the message
 * body outside the development profile, because it can contain single-use links.</p>
 */
public interface EmailSender {

    /**
     * Delivers an email with a plain-text and an HTML body.
     *
     * @param to        the recipient email address
     * @param subject   the subject line
     * @param plainText the plain-text body
     * @param html      the HTML body
     * @throws IllegalStateException when the email cannot be delivered
     */
    void send(String to, String subject, String plainText, String html);
}
