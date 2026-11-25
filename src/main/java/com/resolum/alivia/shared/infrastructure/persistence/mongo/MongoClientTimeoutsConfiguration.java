package com.resolum.alivia.shared.infrastructure.persistence.mongo;

import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * How long the application waits for MongoDB before giving up.
 *
 * <p>The driver waits 30 seconds to find a server when none answers. That is longer than any caller waits: an edge gives
 * up after about 10 seconds, a person after fewer, and {@code /actuator/health} would hang that long while MongoDB is part
 * of it. A short wait turns an outage of Atlas into a quick {@code 503} that the edge retries, instead of a slow one.</p>
 *
 * <p>The values come from {@code alivia.mongo.*} and replace the ones of the connection string, so there is one place to
 * read them. Their defaults are a proposal, to be checked against Atlas from Azure (see
 * {@code docs/operations/tracking-mongodb.md}).</p>
 */
@Configuration
public class MongoClientTimeoutsConfiguration {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoClientTimeouts(
            @Value("${alivia.mongo.server-selection-timeout:PT3S}") Duration serverSelectionTimeout,
            @Value("${alivia.mongo.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${alivia.mongo.read-timeout:PT5S}") Duration readTimeout) {
        require("server-selection-timeout", serverSelectionTimeout);
        require("connect-timeout", connectTimeout);
        require("read-timeout", readTimeout);
        return (MongoClientSettings.Builder builder) -> builder
                .applyToClusterSettings(cluster ->
                        cluster.serverSelectionTimeout(serverSelectionTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout((int) connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout((int) readTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    private static void require(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("alivia.mongo." + name + " must be a positive duration.");
        }
    }
}
