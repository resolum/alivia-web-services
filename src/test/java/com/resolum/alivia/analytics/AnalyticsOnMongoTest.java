package com.resolum.alivia.analytics;

import com.resolum.alivia.analytics.domain.model.aggregates.Metric;
import com.resolum.alivia.analytics.domain.model.valueobjects.MetricKind;
import com.resolum.alivia.analytics.domain.model.valueobjects.SalesFigures;
import com.resolum.alivia.analytics.infrastructure.adapters.MetricRepositoryImpl;
import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.shared.infrastructure.persistence.mongo.MongoTypeMappingConfiguration;
import com.resolum.alivia.tracking.infrastructure.adapters.TrackingStatisticsRepositoryImpl;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The metrics kept by analytics and the statistics tracking gives it, over a real MongoDB. */
@DataMongoTest
@Import({MetricRepositoryImpl.class, TrackingStatisticsRepositoryImpl.class, MongoTypeMappingConfiguration.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class AnalyticsOnMongoTest {

    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-27T00:00:00Z");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        registry.add("spring.mongodb.database", () -> "analytics_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private MetricRepositoryImpl metrics;
    @Autowired
    private TrackingStatisticsRepositoryImpl statistics;

    @BeforeEach
    void empty() {
        mongo.remove(new Query(), "analytics_metrics");
        mongo.remove(new Query(), "device_events");
        mongo.remove(new Query(), "device_states");
    }

    @Test
    void computingTheSamePeriodAgainReplacesTheMetric() {
        var figures = new SalesFigures(1, 1, Map.of("PEN", new BigDecimal("100.00")));
        metrics.save(new Metric(MetricKind.SALES, "all", FROM, TO, FROM, figures, List.of()));
        metrics.save(new Metric(MetricKind.SALES, "all", FROM, TO, TO, figures, List.of("alerts")));

        var kept = mongo.findAll(Document.class, "analytics_metrics");
        assertThat(kept).hasSize(1);
        assertThat(kept.getFirst().getString("kind")).isEqualTo("SALES");
        assertThat(kept.getFirst().getList("incompleteSources", String.class)).containsExactly("alerts");
        assertThat(kept.getFirst().get("values", Document.class).getLong("hirings")).isEqualTo(1L);
    }

    @Test
    void alertsAreTheEventsThatAreNotInformativeAndDevicesAreCountedByConnection() {
        mongo.insert(List.of(
                event("e1", 30L, "HELP_REQUESTED", "CRITICAL", "2026-11-10T10:00:00Z"),
                event("e2", 30L, "LOW_BATTERY", "PREVENTIVE", "2026-11-10T11:00:00Z"),
                event("e3", 30L, "COMMAND_REQUESTED", "INFORMATIVE", "2026-11-10T12:00:00Z"),
                event("e4", 40L, "DEVICE_FAILURE", "IMPORTANT", "2026-10-10T12:00:00Z")), "device_events");
        mongo.insert(List.of(new Document("_id", 1L).append("connectionStatus", "CONNECTED"),
                new Document("_id", 2L).append("connectionStatus", "CONNECTED"),
                new Document("_id", 3L).append("connectionStatus", "DISCONNECTED")), "device_states");

        assertThat(statistics.countAlertsBySeverity(FROM, TO)).isEqualTo(Map.of("CRITICAL", 1L, "PREVENTIVE", 1L));
        assertThat(statistics.countDevicesByConnectionStatus()).isEqualTo(Map.of("CONNECTED", 2L, "DISCONNECTED", 1L));
    }

    private static Document event(String id, Long household, String type, String severity, String at) {
        return new Document("eventId", id).append("edgeId", 1L).append("householdId", household).append("type", type)
                .append("severity", severity).append("origin", "LIVE").append("occurredAt", java.util.Date.from(
                        Instant.parse(at))).append("receivedAt", java.util.Date.from(Instant.parse(at)));
    }
}
