package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidEmailStatusTransition;
import com.resolum.alivia.communications.domain.exceptions.MissingTemplateVariable;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailTemplate;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void createsEmailWithRequiredVariables() {
        Email email = new Email(
                new EmailAddress("test@alivia.pe"),
                EmailTemplate.VERIFICATION_CODE,
                Map.of("code", "123456")
        );

        assertEquals(EmailStatus.PENDING, email.getStatus());
        assertEquals(EmailTemplate.VERIFICATION_CODE, email.getTemplate());
        assertNull(email.getSentAt());
    }

    @Test
    void throwsWhenRequiredVariableIsMissing() {
        assertThrows(MissingTemplateVariable.class, () ->
                new Email(
                        new EmailAddress("test@alivia.pe"),
                        EmailTemplate.VERIFICATION_CODE,
                        Map.of()
                ));
    }

    @Test
    void markSentAndMarkFailedTransitions() {
        Email email1 = new Email(
                new EmailAddress("test@alivia.pe"),
                EmailTemplate.RESET_PASSWORD,
                Map.of("resetUrl", "https://alivia.pe/reset")
        );
        LocalDateTime now = LocalDateTime.now();
        email1.markSent(now);
        assertEquals(EmailStatus.SENT, email1.getStatus());
        assertEquals(now, email1.getSentAt());

        assertThrows(InvalidEmailStatusTransition.class, () -> email1.markFailed("Error"));

        Email email2 = new Email(
                new EmailAddress("test@alivia.pe"),
                EmailTemplate.RESET_PASSWORD,
                Map.of("resetUrl", "https://alivia.pe/reset")
        );
        email2.markFailed("SMTP timeout");
        assertEquals(EmailStatus.FAILED, email2.getStatus());
        assertEquals("SMTP timeout", email2.getFailureReason());
    }
}
