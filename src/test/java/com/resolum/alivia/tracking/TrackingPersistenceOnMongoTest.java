package com.resolum.alivia.tracking;

import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceStateRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.EdgeStatusRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.TelemetrySampleRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceStateDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.EdgeStatusDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.TelemetrySampleDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The persistence of Tracking against a real MongoDB: the indexes the documents declare, the idempotent insertion of
 * events, the conditional write of the last reliable state, the atomic transition of an edge to offline and the
 * time-to-live index of the samples. What a test double cannot show.
 */
@DataMongoTest
@Import({DeviceEventRepositoryImpl.class, DeviceStateRepositoryImpl.class, EdgeStatusRepositoryImpl.class,
        TelemetrySampleRepositoryImpl.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class TrackingPersistenceOnMongoTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:00:00Z");
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();
    private static final ReportingContext CONTEXT = new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L));

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        // One database per run of this class, so nothing of another test is in the way.
        registry.add("spring.mongodb.database", () -> "tracking_" + UUID.randomUUID().toString().replace("-", ""));
        // The context creates the indexes the documents declare, as in production.
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private DeviceEventRepositoryImpl events;
    @Autowired
    private DeviceStateRepositoryImpl states;
    @Autowired
    private EdgeStatusRepositoryImpl edges;
    @Autowired
    private TelemetrySampleRepositoryImpl samples;

    @BeforeEach
    void emptyTheCollectionsButKeepTheIndexes() {
        for (var type : List.of(DeviceEventDocument.class, DeviceStateDocument.class, EdgeStatusDocument.class,
                TelemetrySampleDocument.class)) {
            mongo.remove(new Query(), type);
        }
    }

    private static DeviceEvent event(ReportingContext context, String eventId, long deviceId, Instant at) {
        var reported = new ReportedEvent(eventId, "COMMAND_RESULT", at, deviceId, "cmd-" + eventId, "close", "SUCCESS",
                0.9, null, "puerta cerrada", Map.of("state", "closed", "elapsedMs", 700));
        return DeviceEvent.fromEdge(context, reported, at.plusSeconds(1), false, POLICY);
    }

    private static DeviceState state(long deviceId, Instant observedAt, String currentState) {
        return new DeviceState(deviceId, 3L, 300L, ConnectionStatus.CONNECTED, OperationalStatus.OK, currentState, 78,
                observedAt, observedAt, observedAt.plusSeconds(1), List.of());
    }

    private static List<IndexInfo> indexes(MongoTemplate mongo, Class<?> type) {
        return mongo.indexOps(type).getIndexInfo();
    }

    // ---- indexes

    @Test
    void theDocumentsDeclareTheIndexesTheQueriesNeed() {
        var eventIndexes = indexes(mongo, DeviceEventDocument.class);
        assertThat(eventIndexes).extracting(IndexInfo::getName)
                .contains("edge_event_unique", "household_device_time", "household_time", "command");
        assertThat(eventIndexes.stream().filter(i -> i.getName().equals("edge_event_unique")).findFirst().orElseThrow()
                .isUnique()).isTrue();
        assertThat(eventIndexes.stream().filter(i -> i.getName().equals("command")).findFirst().orElseThrow()
                .getPartialFilterExpression()).contains("commandId");

        assertThat(indexes(mongo, DeviceStateDocument.class)).extracting(IndexInfo::getName).contains("household");
        assertThat(indexes(mongo, EdgeStatusDocument.class)).extracting(IndexInfo::getName).contains("link_last_report");

        var sampleIndexes = indexes(mongo, TelemetrySampleDocument.class);
        assertThat(sampleIndexes).extracting(IndexInfo::getName).contains("household_device_time", "expires");
        assertThat(sampleIndexes.stream().filter(i -> i.getName().equals("expires")).findFirst().orElseThrow()
                .getExpireAfter()).contains(Duration.ZERO);
    }

    // ---- events

    @Test
    void anEventIsKeptOnceAndReadBackWithEverythingItCarries() {
        var original = event(CONTEXT, "e1", 10L, T0);

        assertThat(events.insert(original)).isEqualTo(EventInsertion.INSERTED);

        var read = events.findByEdgeAndEventId(3L, "e1").orElseThrow();
        assertThat(read).isEqualTo(original);
        assertThat(read.attributes().values()).containsEntry("state", "closed").containsEntry("elapsedMs", 700);
        assertThat(read.result()).isEqualTo(EventResult.SUCCESS);
        assertThat(read.type()).isEqualTo(EventType.COMMAND_RESULT);
        assertThat(events.findByEdgeAndEventId(3L, "missing")).isEmpty();
    }

    @Test
    void theSameEventOfTheSameEdgeIsADuplicateAndTheOneOfAnotherEdgeIsNot() {
        var original = event(CONTEXT, "e1", 10L, T0);
        var otherEdge = event(new ReportingContext(4L, 40L, 400L, Set.of(10L)), "e1", 10L, T0);

        assertThat(events.insert(original)).isEqualTo(EventInsertion.INSERTED);
        assertThat(events.insert(original)).isEqualTo(EventInsertion.DUPLICATE);
        assertThat(events.insert(otherEdge)).isEqualTo(EventInsertion.INSERTED);
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(2);
    }

    @Test
    void anEventThatCarriesNothingOptionalIsKeptAndReadBack() {
        var help = DeviceEvent.fromEdge(CONTEXT, new ReportedEvent("h1", "HELP_REQUESTED", T0, null, null, null, null,
                null, null, "ayuda", null), T0.plusSeconds(1), false, POLICY);
        var lost = DeviceEvent.byServer("srv-edge-3-1", EventType.EDGE_DISCONNECTED, 3L, 30L, 300L, null, T0, null);

        events.insert(help);
        events.insert(lost);

        assertThat(events.findByEdgeAndEventId(3L, "h1").orElseThrow()).isEqualTo(help);
        assertThat(events.findByEdgeAndEventId(3L, "srv-edge-3-1").orElseThrow()).isEqualTo(lost);
    }

    @Test
    void sixteenReportsOfTheSameEventAtOnceKeepExactlyOne() throws Exception {
        var original = event(CONTEXT, "race", 10L, T0);
        var inserted = new AtomicInteger();
        var duplicates = new AtomicInteger();

        runAtOnce(16, index -> {
            switch (events.insert(original)) {
                case INSERTED -> inserted.incrementAndGet();
                case DUPLICATE -> duplicates.incrementAndGet();
            }
        });

        assertThat(inserted.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(15);
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(1);
    }

    @Test
    void theHistoryOfADeviceIsPagedNewestFirstAndUsesItsIndex() {
        // Three households with ten devices each, so the planner has a reason to choose the right index.
        var all = new ArrayList<DeviceEventDocument>();
        for (long household = 1; household <= 3; household++) {
            var context = new ReportingContext(household, household * 10, household * 100,
                    Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
            for (long device = 1; device <= 10; device++) {
                for (int i = 0; i < 100; i++) {
                    all.add(DeviceEventDocument.from(event(context, "e-" + household + "-" + device + "-" + i, device,
                            T0.plusSeconds(i))));
                }
            }
        }
        mongo.insert(all, DeviceEventDocument.class);

        var first = events.findByDevice(200L, 5L, T0, T0.plusSeconds(100), 0, 30);
        var second = events.findByDevice(200L, 5L, T0, T0.plusSeconds(100), 1, 30);

        assertThat(first).hasSize(30);
        assertThat(first).allMatch(e -> e.householdId() == 200L && e.deviceId() == 5L);
        assertThat(first.get(0).occurredAt()).isEqualTo(T0.plusSeconds(99));
        assertThat(second.get(0).occurredAt()).isEqualTo(T0.plusSeconds(69));
        // The end of the period is excluded, the start included.
        assertThat(events.findByDevice(200L, 5L, T0.plusSeconds(10), T0.plusSeconds(20), 0, 50)).hasSize(10);
        assertThat(events.findRecentByHousehold(200L, 5)).hasSize(5).allMatch(e -> e.householdId() == 200L);
        // The history of a household never shows the events of another.
        assertThat(events.findByDevice(999L, 5L, T0, T0.plusSeconds(100), 0, 30)).isEmpty();

        var plan = mongo.getCollection("device_events")
                .find(new Document("householdId", 200L).append("deviceId", 5L)
                        .append("occurredAt", new Document("$gte", java.util.Date.from(T0))
                                .append("$lt", java.util.Date.from(T0.plusSeconds(100)))))
                .sort(new Document("occurredAt", -1))
                .explain().toJson();
        assertThat(plan).contains("IXSCAN").contains("household_device_time").doesNotContain("COLLSCAN");
    }

    // ---- last reliable state

    @Test
    void theStateIsKeptUnlessOneObservedLaterIsAlreadyThere() {
        assertThat(states.saveUnlessOlder(state(10L, T0, "closed"))).isTrue();
        assertThat(states.saveUnlessOlder(state(10L, T0.plusSeconds(10), "open"))).isTrue();
        // A late report after an outage does not replace the newer one.
        assertThat(states.saveUnlessOlder(state(10L, T0.plusSeconds(5), "closed"))).isFalse();
        assertThat(states.findByDeviceId(10L).orElseThrow().currentState()).isEqualTo("open");
        // The same observation again is accepted: it is the same state.
        assertThat(states.saveUnlessOlder(state(10L, T0.plusSeconds(10), "open"))).isTrue();
        assertThat(mongo.count(new Query(), DeviceStateDocument.class)).isEqualTo(1);
        assertThat(states.findByDeviceId(99L)).isEmpty();
    }

    @Test
    void aStateReadBackKeepsEveryFieldAndTheListOfDevicesIsFound() {
        var withFault = new DeviceState(11L, 3L, 300L, ConnectionStatus.DISCONNECTED, OperationalStatus.DEGRADED, null,
                null, null, T0, T0.plusSeconds(1), List.of("servo_stuck", "low_signal"));
        states.saveUnlessOlder(state(10L, T0, "closed"));
        states.saveUnlessOlder(withFault);

        assertThat(states.findByDeviceId(11L).orElseThrow()).isEqualTo(withFault);
        assertThat(states.findAllByDeviceIds(List.of(10L, 11L, 12L))).hasSize(2);
        assertThat(states.findAllByDeviceIds(List.of())).isEmpty();
    }

    @Test
    void reportsOfTheSameDeviceAtOnceLeaveTheOneObservedLast() throws Exception {
        var observed = new ArrayList<Integer>();
        for (int i = 0; i < 40; i++) observed.add(i);
        java.util.Collections.shuffle(observed, new java.util.Random(7));

        runAtOnce(8, worker -> {
            for (int i = worker; i < observed.size(); i += 8) {
                int seconds = observed.get(i);
                states.saveUnlessOlder(state(10L, T0.plusSeconds(seconds), "s" + seconds));
            }
        });

        var kept = states.findByDeviceId(10L).orElseThrow();
        assertThat(kept.observedAt()).isEqualTo(T0.plusSeconds(39));
        assertThat(kept.currentState()).isEqualTo("s39");
    }

    // ---- edge link

    @Test
    void aHeartbeatAnswersTheStatusThatWasThereSoTheReconnectionIsKnown() {
        var first = EdgeStatus.heartbeat(null, CONTEXT, T0, 3).status();

        assertThat(edges.recordHeartbeat(first)).isEmpty();
        assertThat(edges.markOffline(3L, T0, T0.plus(Duration.ofMinutes(5)))).isTrue();

        var back = EdgeStatus.heartbeat(null, CONTEXT, T0.plus(Duration.ofMinutes(6)), 12).status();
        var before = edges.recordHeartbeat(back).orElseThrow();
        assertThat(before.link()).isEqualTo(EdgeLink.OFFLINE);
        assertThat(before.offlineSince()).isEqualTo(T0.plus(Duration.ofMinutes(5)));

        var now = edges.findByEdgeId(3L).orElseThrow();
        assertThat(now.link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(now.offlineSince()).isNull();
        assertThat(now.pendingEvents()).isEqualTo(12);
        assertThat(edges.recordHeartbeat(back).orElseThrow().link()).isEqualTo(EdgeLink.ONLINE);
    }

    @Test
    void theFirstReportsOfAnEdgeAtOnceCreateOneStatus() throws Exception {
        var first = EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status();
        var withoutPrevious = new AtomicInteger();

        runAtOnce(8, index -> {
            if (edges.recordHeartbeat(first).isEmpty()) withoutPrevious.incrementAndGet();
        });

        assertThat(withoutPrevious.get()).isEqualTo(1);
        assertThat(mongo.count(new Query(), EdgeStatusDocument.class)).isEqualTo(1);
    }

    @Test
    void severalWatchersAtOnceMarkTheSilentEdgeOfflineExactlyOnce() throws Exception {
        edges.recordHeartbeat(EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status());
        assertThat(edges.findOnlineSilentSince(T0.plus(Duration.ofMinutes(3)))).extracting(EdgeStatus::edgeId)
                .containsExactly(3L);
        assertThat(edges.findOnlineSilentSince(T0)).isEmpty();
        var won = new AtomicInteger();

        runAtOnce(8, index -> {
            if (edges.markOffline(3L, T0, T0.plus(Duration.ofMinutes(10)))) won.incrementAndGet();
        });

        assertThat(won.get()).isEqualTo(1);
        assertThat(edges.findOnlineSilentSince(T0.plus(Duration.ofHours(1)))).isEmpty();
        assertThat(edges.findByEdgeId(3L).orElseThrow().link()).isEqualTo(EdgeLink.OFFLINE);
    }

    @Test
    void anEdgeThatReportedAgainInTheMeantimeIsNotMarkedOffline() {
        edges.recordHeartbeat(EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status());
        edges.recordHeartbeat(EdgeStatus.heartbeat(null, CONTEXT, T0.plusSeconds(100), 0).status());

        // The watcher looked at the status of T0; the edge has reported since.
        assertThat(edges.markOffline(3L, T0, T0.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(edges.findByEdgeId(3L).orElseThrow().link()).isEqualTo(EdgeLink.ONLINE);
    }

    // ---- samples

    @Test
    void samplesAreKeptAndReadNewestFirstAndOnlyThoseWithAnExpirationCarryOne() {
        samples.save(new TelemetrySample(10L, 3L, 300L, T0, new EventAttributes(Map.of("rssi", -55)), null));
        samples.save(new TelemetrySample(10L, 3L, 300L, T0.plusSeconds(30),
                new EventAttributes(Map.of("rssi", -60)), T0.plus(Duration.ofDays(7))));
        samples.save(new TelemetrySample(11L, 3L, 300L, T0, new EventAttributes(Map.of("rssi", -70)), null));

        var read = samples.findByDevice(300L, 10L, T0, T0.plusSeconds(60), 10);

        assertThat(read).hasSize(2);
        assertThat(read.get(0).sampledAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(read.get(0).expiresAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
        assertThat(read.get(1).expiresAt()).isNull();
        // Without an expiration the field is not even written, so the time-to-live index never touches it.
        var raw = mongo.getCollection("telemetry_samples").find(new Document("sampledAt", java.util.Date.from(T0))
                .append("deviceId", 10L)).first();
        assertThat(raw).doesNotContainKey("expiresAt");
    }

    // ---- helpers

    private interface Worker {
        void run(int index) throws Exception;
    }

    /** Runs the same work in {@code threads} threads released at the same moment. */
    private static void runAtOnce(int threads, Worker worker) throws Exception {
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var ready = new CountDownLatch(threads);
            var go = new CountDownLatch(1);
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    worker.run(index);
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (var future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }
    }
}
