package com.resolum.alivia.communications.infrastructure.email.resend;

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

/** Sends transactional emails through the Resend Email API. */
@Slf4j
@Service("emailSender")
@ConditionalOnProperty(prefix = "alivia.communications.email", name = "provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {
    static final String API_URL = "https://api.resend.com/emails";

    private final RestClient client;
    private final String from;

    @Autowired
    public ResendEmailSender(@Value("${alivia.communications.email.resend.api-key}") String apiKey,
                             @Value("${alivia.communications.email.from}") String fromAddress,
                             @Value("${alivia.communications.email.from-name:Alivia}") String fromName) {
        this(RestClient.builder(), apiKey, fromAddress, fromName);
    }

    public ResendEmailSender(RestClient.Builder builder, String apiKey, String fromAddress, String fromName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("RESEND_API_KEY is required when the email provider is resend.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("alivia.communications.email.from is required when the email provider is resend.");
        }
        this.client = builder.baseUrl(API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey.trim())
                .build();
        this.from = fromName == null || fromName.isBlank()
                ? fromAddress.trim()
                : fromName.trim() + " <" + fromAddress.trim() + ">";
    }

    @Override
    public void send(String to, String subject, String plainText, String html) {
        var body = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "text", plainText,
                "html", html);
        try {
            client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("Email handed to Resend");
        } catch (RestClientException exception) {
            throw new IllegalStateException("Email could not be delivered.", exception);
        }
    }
}
