package com.resolum.alivia.tracking;

import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.tracking.application.internal.commandservices.RecordEdgeEventsCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.commands.ReportEventBatchCommand;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.configuration.TrackingConfiguration;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** The urgent report of an edge over a real MongoDB: the event is kept once, however many times the edge resends it. */
@DataMongoTest
@Import({RecordEdgeEventsCommandServiceImpl.class, DeviceEventRepositoryImpl.class, TrackingConfiguration.class,
        RecordEdgeEventsOnMongoTest.Beans.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class RecordEdgeEventsOnMongoTest {

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
    private RecordEdgeEventsCommandServiceImpl service;
    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void home() {
        mongo.remove(new Query(), DeviceEventDocument.class);
        when(assets.fetchReportingEdge(3L)).thenReturn(Optional.of(new ReportingEdge(
                new ReportingContext(3L, 30L, 7L, Set.of(10L)), "ACTUATOR", Map.of(10L, "Puerta principal"))));
        when(notifications.relativesOf(7L)).thenReturn(Set.of(8L));
    }

    @Test
    void anEventResentAfterANetworkLossIsKeptOnceAndNotifiesOnce() {
        // Communications keeps the notification of a source once: the first call is new, the next ones are not.
        var first = new AtomicInteger();
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> first.getAndIncrement() == 0);
        var command = new ReportUrgentEventCommand(3L, "help-1", EventType.HELP_REQUESTED, 10L, null, "ayuda", null);

        assertThat(service.handle(command)).isEqualTo(1);
        assertThat(service.handle(command)).isZero();

        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(1);
        var stored = mongo.findAll(DeviceEventDocument.class).get(0).toDomain();
        assertThat(stored.eventId()).isEqualTo("help-1");
        assertThat(stored.edgeId()).isEqualTo(3L);
        assertThat(stored.householdId()).isEqualTo(7L);
        assertThat(stored.deviceId()).isEqualTo(10L);
        assertThat(stored.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void sixteenResendsAtOnceKeepOneEvent() throws Exception {
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        var command = new ReportUrgentEventCommand(3L, "race", EventType.DEVICE_FAILURE, 10L, null, "sin respuesta", null);
        var pool = Executors.newFixedThreadPool(16);
        try {
            var ready = new CountDownLatch(16);
            var go = new CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 16; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return service.handle(command);
                }));
            }
            ready.await();
            go.countDown();
            for (var future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(1);
    }

    private static ReportEventBatchCommand batchOfTwenty() {
        var events = new java.util.ArrayList<ReportedEvent>();
        for (int i = 0; i < 20; i++) {
            events.add(new ReportedEvent("batch-" + i, "DEVICE_STATE_CHANGED", NOW.minusSeconds(300 - i), 10L, null, null,
                    null, null, null, null, Map.of("state", i % 2 == 0 ? "open" : "closed")));
        }
        return new ReportEventBatchCommand(3L, events);
    }

    @Test
    void aBatchIsKeptOnceInOrderAndSendingItAgainChangesNothing() {
        var first = service.handle(batchOfTwenty());
        var second = service.handle(batchOfTwenty());

        assertThat(first).hasSize(20).allMatch(o -> o.status() == EventOutcome.Status.ACCEPTED);
        assertThat(first.get(0).eventId()).isEqualTo("batch-0");
        assertThat(first.get(19).eventId()).isEqualTo("batch-19");
        assertThat(second).hasSize(20).allMatch(o -> o.status() == EventOutcome.Status.DUPLICATE);
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(20);
        // They arrived minutes after they happened, so they are marked as executed locally.
        assertThat(mongo.findAll(DeviceEventDocument.class)).allMatch(d -> d.toDomain().origin()
                == com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin.SYNCED);
    }

    @Test
    void theSameBatchSentTwiceAtOnceIsAcceptedOnceInTotal() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<java.util.List<EventOutcome>>>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return service.handle(batchOfTwenty());
                }));
            }
            ready.await();
            go.countDown();
            long accepted = 0;
            long duplicates = 0;
            for (var future : futures) {
                for (var outcome : future.get()) {
                    if (outcome.status() == EventOutcome.Status.ACCEPTED) accepted++;
                    if (outcome.status() == EventOutcome.Status.DUPLICATE) duplicates++;
                }
            }
            assertThat(accepted).isEqualTo(20);
            assertThat(duplicates).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(20);
    }
}
