package com.resolum.alivia.shared;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClients;
import com.resolum.alivia.shared.infrastructure.persistence.mongo.MongoClientTimeoutsConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How long the application waits for MongoDB: short, configurable and never zero. */
class MongoClientTimeoutsConfigurationTest {

    private final MongoClientTimeoutsConfiguration configuration = new MongoClientTimeoutsConfiguration();

    private MongoClientSettings built(Duration selection, Duration connect, Duration read) {
        var builder = MongoClientSettings.builder();
        configuration.mongoClientTimeouts(selection, connect, read).customize(builder);
        return builder.build();
    }

    @Test
    void theWaitsAreTheOnesConfigured() {
        var settings = built(Duration.ofSeconds(3), Duration.ofSeconds(4), Duration.ofSeconds(5));

        assertThat(settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(3000);
        assertThat(settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS)).isEqualTo(4000);
        assertThat(settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS)).isEqualTo(5000);
    }

    @Test
    void theDefaultsAreMuchShorterThanTheThirtySecondsOfTheDriver() {
        var settings = built(Duration.parse("PT3S"), Duration.parse("PT3S"), Duration.parse("PT5S"));

        assertThat(settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.SECONDS)).isLessThan(30);
        // The driver's own default, which is what a connection string without options would give.
        assertThat(MongoClientSettings.builder().build().getClusterSettings().getServerSelectionTimeout(TimeUnit.SECONDS))
                .isEqualTo(30);
    }

    @Test
    void aWaitThatIsZeroOrNegativeIsRefusedBecauseItWouldMeanWaitingForEver() {
        assertThatThrownBy(() -> built(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("server-selection-timeout");
        assertThatThrownBy(() -> built(Duration.ofSeconds(3), Duration.ofSeconds(-1), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("connect-timeout");
        assertThatThrownBy(() -> built(Duration.ofSeconds(3), Duration.ofSeconds(3), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("read-timeout");
    }

    @Test
    void withNoServerTheRealDriverGivesUpWhenTheWaitEndsNotAfterThirtySeconds() {
        var builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://127.0.0.1:1/?appName=timeout-test"));
        configuration.mongoClientTimeouts(Duration.ofMillis(800), Duration.ofMillis(500), Duration.ofSeconds(1)).customize(builder);

        long started = System.nanoTime();
        try (var client = MongoClients.create(builder.build())) {
            assertThatThrownBy(() -> client.listDatabaseNames().first()).isInstanceOf(MongoTimeoutException.class);
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).as("ms until it gave up").isGreaterThanOrEqualTo(700).isLessThan(5000);
    }
}
