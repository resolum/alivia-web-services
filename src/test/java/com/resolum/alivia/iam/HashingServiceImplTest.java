package com.resolum.alivia.iam;

import com.resolum.alivia.iam.infrastructure.hashing.bcrypt.services.HashingServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashingServiceImplTest {

    private final HashingServiceImpl service = new HashingServiceImpl();

    @Test
    void encodesAndMatchesPassword() {
        String raw = "MySecurePassword123!";
        String encoded = service.encode(raw);

        assertNotEquals(raw, encoded);
        assertTrue(service.matches(raw, encoded));
    }
}
