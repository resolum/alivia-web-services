package com.resolum.alivia.tracking;

import com.mongodb.ExplainVerbosity;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.queryservices.DeviceTelemetryQueryServiceImpl;
import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.queries.GetDeviceTelemetryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.infrastructure.adapters.TelemetrySampleRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.TelemetrySampleDocument;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Measures the telemetry query (AP-485, the criterion of TS-47 that asks for an answer in under 200 ms) over a real MongoDB
 * with 100 000 readings of 1 000 devices. The test measures and reports; it does not assume.
 *
 * <p><b>What this proves and what it does not.</b> It is a MongoDB in a container on the machine that runs the test, with
 * the index of the collection and a warm cache: it shows that the query is answered from the index and examines only the
 * page, which is what makes it fast. It says nothing about the time over the network to the Atlas M0 cluster of
 * production, which has its own latency and limits; that measurement is manual and is described in
 * {@code docs/tracking-query-contract.md}.</p>
 */
@DataMongoTest
@Import(TelemetrySampleRepositoryImpl.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class TelemetryQueryPerformanceOnMongoTest {

    private static final int DEVICES = 1_000;
    private static final int READINGS_PER_DEVICE = 100;
    private static final int DEVICES_PER_HOME = 10;
    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");
    /** One reading every 5 minutes: the sampling interval of the recorder. */
    private static final Duration STEP = Duration.ofMinutes(5);
    private static final long LIMIT_MS = 200;

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        registry.add("spring.mongodb.database", () -> "tracking_" + UUID.randomUUID().toString().replace("-", ""));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private TelemetrySampleRepositoryImpl samples;

    private final AssetsContextFacade facade = mock(AssetsContextFacade.class);

    private DeviceTelemetryQueryServiceImpl service() {
        return new DeviceTelemetryQueryServiceImpl(new ExternalAssetsService(facade), samples,
                new HistoryPolicy(Duration.ofDays(31), 20, 100), Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    private static long householdOf(long device) {
        return (device - 1) / DEVICES_PER_HOME + 1;
    }

    @BeforeAll
    void hundredThousandReadings() {
        var batch = new ArrayList<TelemetrySampleDocument>();
        for (long device = 1; device <= DEVICES; device++) {
            when(facade.fetchHouseholdOfDevice(device)).thenReturn(Optional.of(householdOf(device)));
            for (int i = 0; i < READINGS_PER_DEVICE; i++) {
                batch.add(TelemetrySampleDocument.from(new TelemetrySample(device, device / 100 + 1, householdOf(device),
                        NOW.minus(STEP.multipliedBy(i + 1)), new EventAttributes(Map.of("rssi", -50 - i % 30, "freeHeap", 180_000 - i)),
                        null)));
            }
            if (batch.size() >= 10_000) {
                mongo.insert(batch, TelemetrySampleDocument.class);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) mongo.insert(batch, TelemetrySampleDocument.class);
    }

    @Test
    void theSeedHasWhatTheMeasurementIsAbout() {
        assertThat(mongo.getCollection("telemetry_samples").countDocuments()).isEqualTo((long) DEVICES * READINGS_PER_DEVICE);
    }

    @Test
    void aPageOfAnyDeviceIsAnsweredInUnderTwoHundredMillisecondsAtTheNinetyFifthPercentile() {
        var service = service();
        var random = new Random(42);
        // The first calls pay for the connection and the class loading, which production does once, not per request.
        for (int i = 0; i < 100; i++) {
            service.handle(new GetDeviceTelemetryQuery((long) random.nextInt(DEVICES) + 1, null, null, 0, 20));
        }

        int calls = 500;
        var millis = new double[calls];
        for (int i = 0; i < calls; i++) {
            var device = (long) random.nextInt(DEVICES) + 1;
            // A mix of the first page and a deep one, since a technician looks at the latest and scrolls back.
            int page = i % 5 == 0 ? 3 : 0;
            long started = System.nanoTime();
            var view = service.handle(new GetDeviceTelemetryQuery(device, null, null, page, 20));
            millis[i] = (System.nanoTime() - started) / 1_000_000.0;
            assertThat(view.total()).isEqualTo(READINGS_PER_DEVICE);
            assertThat(view.items()).hasSize(20);
        }

        Arrays.sort(millis);
        double p50 = millis[calls / 2];
        double p95 = millis[(int) (calls * 0.95) - 1];
        double max = millis[calls - 1];
        System.out.printf("[AP-485] telemetry over %d readings of %d devices, %d calls: p50=%.1f ms, p95=%.1f ms, max=%.1f ms "
                + "(limit %d ms; MongoDB in a local container, not the Atlas cluster)%n",
                DEVICES * READINGS_PER_DEVICE, DEVICES, calls, p50, p95, max, LIMIT_MS);

        assertThat(p95).as("p95 in ms").isLessThan(LIMIT_MS);
    }

    @Test
    void theQueryIsAnsweredFromTheIndexAndExaminesOnlyThePageItReturns() {
        var plan = mongo.getDb().getCollection("telemetry_samples")
                .find(new Document("householdId", householdOf(500)).append("deviceId", 500L)
                        .append("sampledAt", new Document("$gte", java.util.Date.from(NOW.minus(Duration.ofDays(31))))
                                .append("$lt", java.util.Date.from(NOW))))
                .sort(new Document("sampledAt", -1)).skip(60).limit(20)
                .explain(ExplainVerbosity.EXECUTION_STATS);

        assertThat(plan.toJson()).contains("IXSCAN").contains("household_device_time").doesNotContain("COLLSCAN")
                .doesNotContain("\"stage\": \"SORT\"");
        var examined = ((Document) plan.get("executionStats")).get("totalDocsExamined", Number.class).intValue();
        assertThat(examined).isLessThanOrEqualTo(80);
    }
}
