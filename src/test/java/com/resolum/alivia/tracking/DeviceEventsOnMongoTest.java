package com.resolum.alivia.tracking;

import com.mongodb.ExplainVerbosity;
import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.tracking.application.internal.queryservices.DeviceEventsQueryServiceImpl;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.queries.GetDeviceEventsQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The history of a device over a real MongoDB with 8 700 events of several homes: every page is whole and in order,
 * the filters count right, a home never reads another's events, and the query goes through the index.
 */
@DataMongoTest
@Import(DeviceEventRepositoryImpl.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class DeviceEventsOnMongoTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant NOW = T0.plusSeconds(10_000);
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        registry.add("spring.mongodb.database", () -> "tracking_" + UUID.randomUUID().toString().replace("-", ""));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private DeviceEventRepositoryImpl events;

    private final TrackingAccessService access = mock(TrackingAccessService.class);

    private DeviceEventsQueryServiceImpl service() {
        return new DeviceEventsQueryServiceImpl(access, events, new HistoryPolicy(java.time.Duration.ofDays(31), 20, 100),
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    /**
     * Home 200 has device 5 with 4 000 events, one every second, of which one in four is a state change and the rest
     * command results; the rest of the 8 700 are other devices and other homes, among them device 5 itself in home 300,
     * where it was before.
     */
    @BeforeAll
    void tenThousandEvents() {
        var all = new ArrayList<DeviceEventDocument>();
        for (long household : new long[]{200L, 300L, 400L}) {
            var context = new ReportingContext(household, household * 10, household,
                    Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
            for (long device = 1; device <= 10; device++) {
                int count = household == 200L && device == 5L ? 4000 : household == 300L && device == 5L ? 500 : 150;
                for (int i = 0; i < count; i++) {
                    var type = i % 4 == 0 ? "DEVICE_STATE_CHANGED" : "COMMAND_RESULT";
                    var at = T0.plusSeconds(i);
                    var reported = type.equals("COMMAND_RESULT")
                            ? new ReportedEvent("e-" + household + "-" + device + "-" + i, type, at, device, "c-" + i, "open",
                            "SUCCESS", null, null, null, Map.of())
                            : new ReportedEvent("e-" + household + "-" + device + "-" + i, type, at, device, null, null,
                            null, null, null, null, Map.of("state", "open"));
                    all.add(DeviceEventDocument.from(DeviceEvent.fromEdge(context, reported, at.plusSeconds(1), false, POLICY)));
                }
            }
        }
        mongo.insert(all, DeviceEventDocument.class);
        when(access.requireReadableHouseholdOfDevice(7L, 5L)).thenReturn(200L);
    }

    private GetDeviceEventsQuery query(Instant from, Instant to, String type, int page, int size) {
        return new GetDeviceEventsQuery(7L, 5L, from, to, type, page, size);
    }

    @Test
    void everyPageIsWholeInOrderAndTogetherTheyAreTheWholePeriodWithoutRepeating() {
        var seen = new HashSet<String>();
        Instant previous = null;
        for (int page = 0; ; page++) {
            var view = service().handle(query(T0, NOW, null, page, 100));
            assertThat(view.total()).isEqualTo(4000);
            assertThat(view.totalPages()).isEqualTo(40);
            if (view.items().isEmpty()) {
                assertThat(page).isEqualTo(40);
                break;
            }
            assertThat(view.items()).hasSize(100);
            for (var event : view.items()) {
                assertThat(event.householdId()).isEqualTo(200L);
                assertThat(event.deviceId()).isEqualTo(5L);
                assertThat(seen.add(event.eventId())).isTrue();
                if (previous != null) assertThat(event.occurredAt()).isBeforeOrEqualTo(previous);
                previous = event.occurredAt();
            }
        }
        assertThat(seen).hasSize(4000);
    }

    @Test
    void theNewestEventIsFirstAndTheEndOfThePeriodIsExcluded() {
        var view = service().handle(query(T0, T0.plusSeconds(100), null, 0, 5));

        assertThat(view.total()).isEqualTo(100);
        assertThat(view.items().get(0).occurredAt()).isEqualTo(T0.plusSeconds(99));
        assertThat(view.items().get(4).occurredAt()).isEqualTo(T0.plusSeconds(95));
    }

    @Test
    void theFilterByTypeCountsOnlyThatType() {
        var changes = service().handle(query(T0, NOW, "device_state_changed", 0, 10));
        var results = service().handle(query(T0, NOW, "COMMAND_RESULT", 0, 10));

        assertThat(changes.total()).isEqualTo(1000);
        assertThat(changes.items()).hasSize(10).allMatch(e -> e.type() == EventType.DEVICE_STATE_CHANGED);
        assertThat(results.total()).isEqualTo(3000);
        assertThat(service().handle(query(T0, NOW, "HELP_REQUESTED", 0, 10)).total()).isZero();
    }

    @Test
    void aHomeNeverReadsWhatTheDeviceDidInAnotherHome() {
        // Device 5 has 500 events in home 300 in the same period: none of them is shown to home 200.
        var view = service().handle(query(T0, NOW, null, 0, 100));

        assertThat(view.total()).isEqualTo(4000);
        assertThat(events.findByDevice(300L, 5L, T0, NOW, null, 0, 1).total()).isEqualTo(500);
    }

    @Test
    void aPageBeyondTheEndIsEmptyButStillSaysHowManyThereAre() {
        var view = service().handle(query(T0, NOW, null, 1000, 100));

        assertThat(view.items()).isEmpty();
        assertThat(view.total()).isEqualTo(4000);
    }

    @Test
    void thePeriodWithoutEventsIsEmpty() {
        var view = service().handle(query(T0.plusSeconds(5000), NOW, null, 0, 20));

        assertThat(view.items()).isEmpty();
        assertThat(view.total()).isZero();
        assertThat(view.totalPages()).isZero();
    }

    @Test
    void theSearchGoesThroughTheIndexOfTheDeviceAndExaminesOnlyThePage() {
        var execution = mongo.getDb().getCollection("device_events")
                .find(new Document("householdId", 200L).append("deviceId", 5L)
                        .append("occurredAt", new Document("$gte", java.util.Date.from(T0))
                                .append("$lt", java.util.Date.from(NOW))))
                .sort(new Document("occurredAt", -1)).skip(300).limit(100)
                .explain(ExplainVerbosity.EXECUTION_STATS);

        var json = execution.toJson();
        assertThat(json).contains("IXSCAN").contains("household_device_time").doesNotContain("COLLSCAN")
                // The index gives the order: no sort of the matches in memory.
                .doesNotContain("\"stage\": \"SORT\"");
        var examined = ((Document) execution.get("executionStats")).get("totalDocsExamined", Number.class).intValue();
        assertThat(examined).isLessThanOrEqualTo(400);
    }

    /** What an event costs, measured in document bytes (the indexes are estimated: see the note of the telemetry test). */
    @Test
    void measuresWhatAnEventCostsInDocumentBytes() {
        var stats = mongo.getDb().runCommand(new Document("collStats", "device_events"));
        long count = ((Number) stats.get("count")).longValue();
        long data = ((Number) stats.get("size")).longValue();
        var raw = mongo.getCollection("device_events").find().first();

        double perEvent = (double) data / count;
        System.out.printf("[M0] device_events, %d events: %.0f bytes per document (%d bytes in all), fields: %s%n",
                count, perEvent, data, raw.keySet());

        assertThat(count).isEqualTo(8_700);
        assertThat(perEvent).as("bytes per event document").isLessThan(1024);
    }
}
