package com.resolum.alivia.shared.infrastructure.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Shared clock configuration.
 */
@Configuration
public class ClockConfiguration {

    /**
     * Provides a UTC clock when the application does not define one.
     *
     * @return the UTC clock
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}
