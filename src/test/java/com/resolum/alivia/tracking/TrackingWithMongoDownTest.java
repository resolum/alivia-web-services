package com.resolum.alivia.tracking;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.resolum.alivia.shared.infrastructure.persistence.mongo.MongoClientTimeoutsConfiguration;
import com.resolum.alivia.tracking.application.internal.commandservices.RecordEdgeEventsCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.commandservices.ReportDeviceStatesCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.commandservices.TelemetrySampleRecorder;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.commands.ReportDeviceStatesCommand;
import com.resolum.alivia.tracking.domain.model.commands.ReportEventBatchCommand;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceStateRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.EdgeStatusRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.TelemetrySampleRepositoryImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tracking with the real MongoDB driver pointed at a server that is not there. The other tests make the store fail with a
 * double; this one is the nearest thing to an Atlas outage that needs no Docker: the driver really tries, waits the
 * configured time and gives up.
 *
 * <p>What it shows: a request for help still reaches the family and is answered; everything the edge must not forget is
 * answered {@code 503} so it sends it again; and neither waits the 30 seconds the driver waited before the timeouts.</p>
 */
class TrackingWithMongoDownTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();

    private final ExternalAssetsService assets = mock(ExternalAssetsService.class);
    private final ExternalNotificationsService notifications = mock(ExternalNotificationsService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private MongoClient client;
    private MongoTemplate mongo;

    @BeforeEach
    void noServer() {
        var builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://127.0.0.1:1/?appName=tracking-down"));
        new MongoClientTimeoutsConfiguration()
                .mongoClientTimeouts(Duration.ofMillis(400), Duration.ofMillis(300), Duration.ofSeconds(1)).customize(builder);
        client = MongoClients.create(builder.build());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "tracking_down"));
        when(assets.fetchReportingEdge(3L)).thenReturn(Optional.of(new ReportingEdge(
                new ReportingContext(3L, 30L, 300L, Set.of(10L)), "ACTUATOR", Map.of(10L, "Puerta principal"))));
        when(notifications.relativesOf(300L)).thenReturn(Set.of(8L));
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    @AfterEach
    void close() {
        client.close();
    }

    private RecordEdgeEventsCommandServiceImpl events() {
        return new RecordEdgeEventsCommandServiceImpl(assets, notifications, new DeviceEventRepositoryImpl(mongo), POLICY,
                Clock.fixed(NOW, ZoneOffset.UTC), meters);
    }

    private double failures(String operation) {
        var counter = meters.find("tracking.storage.failures").tag("operation", operation).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aRequestForHelpReachesTheFamilyAndIsAnsweredEvenThoughItCannotBeKept() {
        long started = System.nanoTime();

        int told = events().handle(new ReportUrgentEventCommand(3L, "help-1", EventType.HELP_REQUESTED, null, null, "ayuda", null));

        assertThat(told).isEqualTo(1);
        verify(notifications).notifyOnce(8L, "HELP_REQUESTED", "edge-help-1", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa: «ayuda».");
        assertThat(failures("insert_event")).isEqualTo(1.0);
        assertThat((System.nanoTime() - started) / 1_000_000).as("ms").isLessThan(5000);
    }

    @Test
    void aBatchIsNotAcknowledgedAndTheEdgeIsToldToSendItAgain() {
        var batch = new ReportEventBatchCommand(3L, List.of(new ReportedEvent("e-1", "DEVICE_STATE_CHANGED",
                NOW.minusSeconds(10), 10L, null, null, null, null, null, null, Map.of("state", "open"))));
        long started = System.nanoTime();

        assertThatThrownBy(() -> events().handle(batch)).isInstanceOf(TrackingStorageUnavailable.class);

        assertThat(failures("insert_event")).isEqualTo(1.0);
        assertThat((System.nanoTime() - started) / 1_000_000).as("ms").isLessThan(5000);
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aRequestForHelpInABatchTellsTheFamilyOnceEvenWhenTheEdgeMustSendTheBatchAgain() {
        var batch = new ReportEventBatchCommand(3L, List.of(new ReportedEvent("help-2", "HELP_REQUESTED",
                NOW.minusSeconds(10), null, null, null, null, null, null, "ayuda", null)));

        assertThatThrownBy(() -> events().handle(batch)).isInstanceOf(TrackingStorageUnavailable.class);
        // The edge sends it again; Communications knows the source, so nobody is told twice.
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> events().handle(batch)).isInstanceOf(TrackingStorageUnavailable.class);

        verify(notifications, org.mockito.Mockito.times(2)).notifyOnce(8L, "HELP_REQUESTED", "edge-help-2",
                "Solicitud de asistencia", "Tu familiar pidió ayuda desde casa: «ayuda».");
    }

    @Test
    void aReportOfStatesIsNotAcknowledgedEither() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var recorder = new TelemetrySampleRecorder(new TelemetrySampleRepositoryImpl(mongo), clock, Duration.ofMinutes(5),
                null, meters, Runnable::run);
        var service = new ReportDeviceStatesCommandServiceImpl(assets, new DeviceStateRepositoryImpl(mongo),
                new EdgeStatusRepositoryImpl(mongo), new DeviceEventRepositoryImpl(mongo), recorder, notifications, POLICY,
                clock, meters);
        var at = NOW.minusSeconds(5);
        var report = new ReportDeviceStatesCommand(3L, 0, List.of(new ReportedDeviceState(10L, "CONNECTED", "OK", "closed",
                80, at, at, List.of(), Map.of("rssi", -55))));
        long started = System.nanoTime();

        assertThatThrownBy(() -> service.handle(report)).isInstanceOf(TrackingStorageUnavailable.class);

        assertThat(failures("report_states")).isEqualTo(1.0);
        assertThat((System.nanoTime() - started) / 1_000_000).as("ms").isLessThan(5000);
    }
}
