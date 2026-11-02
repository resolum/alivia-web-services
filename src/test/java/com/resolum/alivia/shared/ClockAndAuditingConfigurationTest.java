package com.resolum.alivia.shared;

import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockAndAuditingConfigurationTest {

    @Test
    void providesClockAndAuditingDateTimeProvider() {
        var clockConfig = new ClockConfiguration();
        Clock clock = clockConfig.clock();
        assertNotNull(clock);

        var auditingConfig = new JpaAuditingConfiguration();
        var provider = auditingConfig.auditingDateTimeProvider(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        assertNotNull(provider);
        assertTrue(provider.getNow().isPresent());
    }
}
