package com.resolum.alivia.tracking;

import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.tracking.application.internal.commandservices.ReportDeviceStatesCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.commands.ReportDeviceStatesCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.StateOutcome;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceStateRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.EdgeStatusRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.TelemetrySampleRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.configuration.TrackingConfiguration;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceStateDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.EdgeStatusDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.TelemetrySampleDocument;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The report of device states over a real MongoDB: what the atomic writes guarantee when the same report, or two reports
 * of the same edge, arrive at once.
 */
@DataMongoTest
@Import({ReportDeviceStatesCommandServiceImpl.class, DeviceEventRepositoryImpl.class, DeviceStateRepositoryImpl.class,
        EdgeStatusRepositoryImpl.class, TelemetrySampleRepositoryImpl.class, TrackingConfiguration.class,
        ReportDeviceStatesOnMongoTest.Beans.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class ReportDeviceStatesOnMongoTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        registry.add("spring.mongodb.database", () -> "tracking_" + UUID.randomUUID().toString().replace("-", ""));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @MockitoBean
    private ExternalAssetsService assets;
    @MockitoBean
    private ExternalNotificationsService notifications;
    @Autowired
    private ReportDeviceStatesCommandServiceImpl service;
    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void home() {
        mongo.remove(new Query(), DeviceEventDocument.class);
        mongo.remove(new Query(), DeviceStateDocument.class);
        mongo.remove(new Query(), EdgeStatusDocument.class);
        mongo.remove(new Query(), TelemetrySampleDocument.class);
        when(assets.fetchReportingEdge(3L)).thenReturn(Optional.of(new ReportingEdge(
                new ReportingContext(3L, 30L, 7L, Set.of(10L, 11L)), "ACTUATOR", Map.of())));
    }

    private static ReportDeviceStatesCommand report(String connection, String observedAt) {
        var at = Instant.parse(observedAt);
        return new ReportDeviceStatesCommand(3L, 0, List.of(new ReportedDeviceState(10L, connection, "OK", "closed", 80,
                at, at, List.of(), Map.of("rssi", -55))));
    }

    private List<EventType> eventTypes() {
        return mongo.findAll(DeviceEventDocument.class).stream().map(d -> d.toDomain().type()).toList();
    }

    @Test
    void theStateOfADeviceAndTheHeartbeatOfItsEdgeAreKept() {
        var outcome = service.handle(report("CONNECTED", "2026-11-24T15:04:00Z"));

        assertThat(outcome.get(0).status()).isEqualTo(StateOutcome.APPLIED);
        var state = mongo.findAll(DeviceStateDocument.class);
        assertThat(state).hasSize(1);
        var edge = mongo.findAll(EdgeStatusDocument.class);
        assertThat(edge).hasSize(1);
        assertThat(edge.get(0).toDomain().link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(edge.get(0).toDomain().lastReportAt()).isEqualTo(NOW);
    }

    @Test
    void theSameDisconnectionSentSixteenTimesAtOnceLeavesOneEvent() throws Exception {
        service.handle(report("CONNECTED", "2026-11-24T15:00:00Z"));
        var down = report("DISCONNECTED", "2026-11-24T15:01:00Z");

        runAtOnce(16, () -> service.handle(down));

        assertThat(eventTypes()).containsExactly(EventType.DEVICE_DISCONNECTED);
    }

    @Test
    void anEdgeThatWasOfflineIsReconnectedOnceHoweverManyReportsArriveAtOnce() throws Exception {
        service.handle(new ReportDeviceStatesCommand(3L, 0, List.of()));
        var silentSince = Instant.parse("2026-11-24T14:30:00Z");
        mongo.updateFirst(new Query(), new org.springframework.data.mongodb.core.query.Update()
                .set("link", "OFFLINE").set("offlineSince", silentSince), EdgeStatusDocument.class);

        runAtOnce(8, () -> service.handle(new ReportDeviceStatesCommand(3L, 0, List.of())));

        assertThat(eventTypes()).containsExactly(EventType.EDGE_RECONNECTED);
        assertThat(mongo.findAll(EdgeStatusDocument.class).get(0).toDomain().link()).isEqualTo(EdgeLink.ONLINE);
    }

    @Test
    void aLateReportNeverReplacesTheStateAddedByALaterOne() {
        service.handle(report("CONNECTED", "2026-11-24T15:03:00Z"));

        var late = service.handle(report("DISCONNECTED", "2026-11-24T15:01:00Z"));

        assertThat(late.get(0).status()).isEqualTo(StateOutcome.STALE);
        assertThat(mongo.findAll(DeviceStateDocument.class).get(0).toDomain().observedAt())
                .isEqualTo(Instant.parse("2026-11-24T15:03:00Z"));
        assertThat(eventTypes()).isEmpty();
    }

    @Test
    void theDiagnosticsAreKeptInTheBackgroundWithoutExpirationWhenNoRetentionIsSet() throws Exception {
        // The recorder remembers when each device was last sampled and the clock is fixed, so this test uses a device
        // no other test reports.
        var at = Instant.parse("2026-11-24T15:04:00Z");
        service.handle(new ReportDeviceStatesCommand(3L, 0, List.of(new ReportedDeviceState(11L, "CONNECTED", "OK",
                "open", null, at, at, List.of(), Map.of("rssi", -60)))));

        long deadline = System.currentTimeMillis() + 5000;
        while (mongo.count(new Query(), TelemetrySampleDocument.class) == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        var samples = mongo.findAll(TelemetrySampleDocument.class);
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).toDomain().expiresAt()).isNull();
    }

    private static void runAtOnce(int threads, Runnable task) throws Exception {
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var ready = new CountDownLatch(threads);
            var go = new CountDownLatch(1);
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    task.run();
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
