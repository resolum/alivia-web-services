package com.resolum.alivia.tracking;

import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.shared.infrastructure.persistence.mongo.MongoTypeMappingConfiguration;
import com.resolum.alivia.tracking.application.acl.TrackingContextFacadeImpl;
import com.resolum.alivia.tracking.application.internal.queryservices.AutonomyQueryServiceImpl;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.EdgeStatusRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.EdgeStatusDocument;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade.AutonomyCounts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The autonomy summary over a real MongoDB: the events go in through the same repository the edges use (so a resend is
 * refused by the unique index), and what comes out is what analytics would count.
 */
@DataMongoTest
@Import({DeviceEventRepositoryImpl.class, EdgeStatusRepositoryImpl.class, MongoTypeMappingConfiguration.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class AutonomySummaryOnMongoTest {

    private static final Instant FROM = Instant.parse("2026-11-24T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-25T00:00:00Z");
    private static final Instant T = Instant.parse("2026-11-24T10:00:00Z");
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();
    private static final ReportingContext HOME = new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L));
    private static final ReportingContext OTHER_HOME = new ReportingContext(4L, 40L, 400L, Set.of(10L, 11L));

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
    @Autowired
    private EdgeStatusRepositoryImpl edges;

    private TrackingContextFacade facade() {
        return new TrackingContextFacadeImpl(new AutonomyQueryServiceImpl(events, edges, new SimpleMeterRegistry()));
    }

    @BeforeEach
    void empty() {
        mongo.remove(new Query(), DeviceEventDocument.class);
        mongo.remove(new Query(), EdgeStatusDocument.class);
    }

    private void send(ReportingContext home, String eventId, String type, Instant at, Long device, String command,
                      String action, String result, boolean late) {
        var received = late ? at.plusSeconds(3600) : at.plusSeconds(1);
        events.insert(DeviceEvent.fromEdge(home, new ReportedEvent(eventId, type, at, device, command, action, result, null,
                null, null, null), received, late, POLICY));
    }

    private void request(String eventId, String command, long device, int seconds) {
        send(HOME, eventId, "COMMAND_REQUESTED", T.plusSeconds(seconds), device, command, "open", null, false);
    }

    private void result(String eventId, String command, long device, String result, int seconds, boolean late) {
        send(HOME, eventId, "COMMAND_RESULT", T.plusSeconds(seconds), device, command, "open", result, late);
    }

    @Test
    void theCommandsOfTheStoryAreCountedByOutcomeAndByDevice() {
        request("r-ok", "ok", 10, 0);
        result("x-ok", "ok", 10, "SUCCESS", 2, false);
        request("r-bad", "bad", 10, 10);
        result("x-bad", "bad", 10, "FAILED", 12, false);
        send(HOME, "j-low", "COMMAND_REJECTED", T.plusSeconds(20), null, "low", null, "REJECTED_LOW_CONFIDENCE", false);
        request("r-silent", "silent", 11, 30);
        result("x-silent", "silent", 11, "NO_CONFIRMATION", 35, false);
        request("r-pending", "pending", 11, 40);
        request("r-d11", "d11", 11, 50);
        result("x-d11", "d11", 11, "SUCCESS", 51, false);

        var summary = facade().summarizeAutonomy(300L, null, FROM, TO);

        assertThat(summary.total()).isEqualTo(new AutonomyCounts(6, 2, 1, 1, 0, 1, 1, 0));
        assertThat(summary.byDevice()).extracting(TrackingContextFacade.DeviceAutonomy::deviceId).containsExactly(10L, 11L);
        assertThat(summary.byDevice().get(0).counts()).isEqualTo(new AutonomyCounts(2, 1, 1, 0, 0, 0, 0, 0));
        assertThat(summary.byDevice().get(1).counts()).isEqualTo(new AutonomyCounts(3, 1, 0, 0, 0, 1, 1, 0));
        assertThat(summary.withoutDevice()).isEqualTo(new AutonomyCounts(1, 0, 0, 1, 0, 0, 0, 0));
    }

    @Test
    void anEventSentAgainIsCountedOnceWhetherItIsTheSameEventOrTheSameCommandAgain() {
        request("r-1", "c1", 10, 0);
        result("x-1", "c1", 10, "SUCCESS", 2, false);
        // The edge resends the same events after a loss, and also emits the result again under a new event id.
        request("r-1", "c1", 10, 0);
        result("x-1", "c1", 10, "SUCCESS", 2, false);
        result("x-1-again", "c1", 10, "SUCCESS", 2, false);

        var summary = facade().summarizeAutonomy(300L, null, FROM, TO);

        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(3);
        assertThat(summary.total()).isEqualTo(new AutonomyCounts(1, 1, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void whatWasExecutedWithoutConnectionAndSyncedLaterIsCountedOnceAndMarkedLocal() {
        request("r-1", "c1", 10, 0);
        result("x-1-silent", "c1", 10, "NO_CONFIRMATION", 5, false);
        // The actuator had done it and the result reaches the backend an hour later, through the batch.
        result("x-1", "c1", 10, "SUCCESS", 3, true);
        result("x-1", "c1", 10, "SUCCESS", 3, true);

        var total = facade().summarizeAutonomy(300L, null, FROM, TO).total();

        assertThat(total).isEqualTo(new AutonomyCounts(1, 1, 0, 0, 0, 0, 0, 1));
    }

    @Test
    void theDeviceAndThePeriodNarrowTheCount() {
        request("r-a", "a", 10, 0);
        result("x-a", "a", 10, "SUCCESS", 1, false);
        request("r-b", "b", 11, 0);
        result("x-b", "b", 11, "FAILED", 1, false);
        send(HOME, "j-low", "COMMAND_REJECTED", T, null, "low", null, "REJECTED_LOW_CONFIDENCE", false);
        // The end of the period is excluded, the start is included.
        send(HOME, "edge-end", "COMMAND_REQUESTED", TO, 10L, "at-end", "open", null, false);
        send(HOME, "edge-start", "COMMAND_REQUESTED", FROM, 10L, "at-start", "open", null, false);

        var onlyTenToday = facade().summarizeAutonomy(300L, 10L, FROM, TO);
        var firstHalf = facade().summarizeAutonomy(300L, null, FROM, T.plusSeconds(60));

        assertThat(onlyTenToday.deviceId()).isEqualTo(10L);
        assertThat(onlyTenToday.total()).isEqualTo(new AutonomyCounts(2, 1, 0, 0, 0, 0, 1, 0));
        // A rejection that names no device is not part of a summary limited to a device.
        assertThat(onlyTenToday.withoutDevice().requested()).isZero();
        assertThat(firstHalf.total().requested()).isEqualTo(4);
        assertThat(facade().summarizeAutonomy(300L, null, T.plusSeconds(1000), TO).total().requested()).isZero();
    }

    @Test
    void aHomeNeverCountsTheCommandsOfAnother() {
        request("r-a", "a", 10, 0);
        send(OTHER_HOME, "r-other", "COMMAND_REQUESTED", T, 10L, "other", "open", null, false);
        send(OTHER_HOME, "x-other", "COMMAND_RESULT", T.plusSeconds(1), 10L, "other", "open", "SUCCESS", false);

        assertThat(facade().summarizeAutonomy(300L, null, FROM, TO).total().requested()).isEqualTo(1);
        assertThat(facade().summarizeAutonomy(400L, null, FROM, TO).total().succeeded()).isEqualTo(1);
    }

    @Test
    void eventsThatAreNotAboutCommandsAreNotCounted() {
        send(HOME, "h-1", "HELP_REQUESTED", T, null, null, null, null, false);
        send(HOME, "s-1", "DEVICE_STATE_CHANGED", T, 10L, null, null, null, false);
        send(HOME, "l-1", "LOW_BATTERY", T, 10L, null, null, null, false);

        assertThat(facade().summarizeAutonomy(300L, null, FROM, TO).total().requested()).isZero();
    }

    @Test
    void anEdgeThatIsNotHeardGivesTheDateOfTheLastUpdateAndPendingEvents() {
        edges.recordHeartbeat(new EdgeStatus(3L, 30L, 300L, T, EdgeLink.ONLINE, 0, null));
        edges.markOffline(3L, T, T.plusSeconds(200));

        var summary = facade().summarizeAutonomy(300L, null, FROM, TO);

        assertThat(summary.edgeLink()).isEqualTo("OFFLINE");
        assertThat(summary.lastUpdatedAt()).isEqualTo(T);
        assertThat(summary.mayHavePendingEvents()).isTrue();
    }

    @Test
    void theCommandEventsAreReadWithOnlyWhatTheCountNeeds() {
        // Nothing of what was said can reach the metrics: the fact has no detail, no attributes and no confidence.
        var components = java.util.Arrays.stream(
                com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(components).containsExactly("commandId", "type", "result", "origin", "occurredAt", "deviceId");
    }
}
