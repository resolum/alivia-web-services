package com.resolum.alivia.communications.infrastructure.email.sendgrid;

import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * {@link EmailSender} backed by the SendGrid v3 Mail Send API.
 *
 * <p>Active only when {@code alivia.communications.email.provider=sendgrid}. The API key comes from
 * {@code SENDGRID_API_KEY} and the sender must be verified in SendGrid (single sender or authenticated domain).
 */
@Slf4j
@Service("emailSender")
@ConditionalOnProperty(prefix = "alivia.communications.email", name = "provider", havingValue = "sendgrid")
public class SendGridEmailSender implements EmailSender {
    static final String API_URL = "https://api.sendgrid.com/v3/mail/send";

    private final RestClient client;
    private final String fromAddress;
    private final String fromName;

    @Autowired
    public SendGridEmailSender(@Value("${alivia.communications.email.sendgrid.api-key}") String apiKey,
                               @Value("${alivia.communications.email.from}") String fromAddress,
                               @Value("${alivia.communications.email.from-name:Alivia}") String fromName) {
        this(RestClient.builder(), apiKey, fromAddress, fromName);
    }

    /** Builds the sender on a given client builder, so tests can intercept the HTTP calls. */
    public SendGridEmailSender(RestClient.Builder builder, String apiKey, String fromAddress, String fromName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SENDGRID_API_KEY is required when the email provider is sendgrid.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("alivia.communications.email.from is required when the email provider is sendgrid.");
        }
        this.client = builder.baseUrl(API_URL).defaultHeader("Authorization", "Bearer " + apiKey.trim()).build();
        this.fromAddress = fromAddress.trim();
        this.fromName = fromName;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when SendGrid rejects the message or cannot be reached
     */
    @Override
    public void send(String to, String subject, String plainText, String html) {
        var body = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                "from", Map.of("email", fromAddress, "name", fromName),
                "subject", subject,
                // SendGrid requires text/plain before text/html.
                "content", List.of(Map.of("type", "text/plain", "value", plainText),
                        Map.of("type", "text/html", "value", html)));
        try {
            client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("Email handed to SendGrid");
        } catch (RestClientException exception) {
            throw new IllegalStateException("Email could not be delivered.", exception);
        }
    }
}
