package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailVerificationTokenServiceTest {

    @Test
    void generatedTokenInstantiatesWithValidValues() {
        var domainToken = new EmailVerificationToken("hash123", LocalDateTime.now());
        var generated = new EmailVerificationTokenService.GeneratedToken("raw123", domainToken);

        assertEquals("raw123", generated.rawToken());
        assertEquals(domainToken, generated.token());
    }

    @Test
    void generatedTokenRejectsInvalidValues() {
        var domainToken = new EmailVerificationToken("hash123", LocalDateTime.now());

        assertThrows(IllegalArgumentException.class,
                () -> new EmailVerificationTokenService.GeneratedToken(null, domainToken));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailVerificationTokenService.GeneratedToken("   ", domainToken));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailVerificationTokenService.GeneratedToken("raw123", null));
    }
}
