package com.resolum.alivia.tracking;

import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.shared.infrastructure.persistence.mongo.MongoTypeMappingConfiguration;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.application.internal.tasks.EdgeLinkWatcher;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.EdgeStatusRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.EdgeStatusDocument;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The watcher over a real MongoDB: several instances at once make one transition per edge, and what it needs to decide is
 * in the database, not in the memory of a process that may have restarted.
 */
@DataMongoTest
@Import({DeviceEventRepositoryImpl.class, EdgeStatusRepositoryImpl.class, MongoTypeMappingConfiguration.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class EdgeLinkWatcherOnMongoTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:00:00Z");
    private static final Instant LATER = T0.plus(Duration.ofMinutes(10));
    private static final Duration AFTER = Duration.ofMinutes(3);

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        registry.add("spring.mongodb.database", () -> "tracking_" + UUID.randomUUID().toString().replace("-", ""));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @MockitoBean
    private ExternalNotificationsService notifications;
    @MockitoBean
    private ExternalAssetsService assets;
    @Autowired
    private EdgeStatusRepositoryImpl edges;
    @Autowired
    private DeviceEventRepositoryImpl events;
    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void silentEdges() {
        mongo.remove(new Query(), DeviceEventDocument.class);
        mongo.remove(new Query(), EdgeStatusDocument.class);
        when(notifications.relativesOf(anyLong())).thenReturn(Set.of(8L));
        // Every edge of the test is in the home its status says (installation 30 + edge, household 300 + edge).
        when(assets.fetchReportingEdge(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return java.util.Optional.of(new ExternalAssetsService.ReportingEdge(
                    new ReportingContext(id, 30 + id, 300 + id, Set.of()), "ACTUATOR", java.util.Map.of()));
        });
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        for (long edge = 1; edge <= 10; edge++) {
            edges.recordHeartbeat(new EdgeStatus(edge, 30 + edge, 300 + edge, T0, EdgeLink.ONLINE, 0, null));
        }
    }

    private EdgeLinkWatcher watcherAt(Instant now, Duration grace) {
        return new EdgeLinkWatcher(edges, events, assets, notifications, Clock.fixed(now, ZoneOffset.UTC),
                new SimpleMeterRegistry(), AFTER, grace);
    }

    @Test
    void fourInstancesAtOnceMarkEveryEdgeOnceAndWriteOneEventEach() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        int marked = 0;
        try {
            var ready = new CountDownLatch(4);
            var go = new CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                var watcher = watcherAt(LATER, Duration.ZERO);
                futures.add(pool.submit((Callable<Integer>) () -> {
                    ready.countDown();
                    go.await();
                    return watcher.markSilentEdges();
                }));
            }
            ready.await();
            go.countDown();
            for (var future : futures) marked += future.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(marked).isEqualTo(10);
        var written = mongo.findAll(DeviceEventDocument.class);
        assertThat(written).hasSize(10).allMatch(d -> d.toDomain().type() == EventType.EDGE_DISCONNECTED);
        assertThat(mongo.findAll(EdgeStatusDocument.class)).hasSize(10).allMatch(d -> {
            var status = d.toDomain();
            return status.link() == EdgeLink.OFFLINE && status.offlineSince().equals(LATER) && status.lastReportAt().equals(T0);
        });
    }

    @Test
    void anotherRunAfterwardsFindsNothingToDo() {
        watcherAt(LATER, Duration.ZERO).markSilentEdges();

        assertThat(watcherAt(LATER.plusSeconds(30), Duration.ZERO).markSilentEdges()).isZero();

        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(10);
    }

    @Test
    void anEdgeThatReportsAgainComesBackOnlineAndCanBeMarkedAgainLater() {
        watcherAt(LATER, Duration.ZERO).markSilentEdges();
        var back = LATER.plusSeconds(20);

        var before = edges.recordHeartbeat(new EdgeStatus(3L, 33L, 303L, back, EdgeLink.ONLINE, 0, null));

        assertThat(before).isPresent();
        assertThat(before.get().link()).isEqualTo(EdgeLink.OFFLINE);
        assertThat(edges.findByEdgeId(3L).orElseThrow().link()).isEqualTo(EdgeLink.ONLINE);

        // It goes silent again: this is a new outage, with its own event.
        var muchLater = back.plus(Duration.ofMinutes(10));
        assertThat(watcherAt(muchLater, Duration.ZERO).markSilentEdges()).isEqualTo(1);
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(11);
    }

    @Test
    void aRestartedBackendDoesNotJudgeAnyoneDuringTheGraceAndThenUsesWhatIsStored() {
        // A new process: nothing in memory, and the edges' last reports are in the database from before.
        var restarted = watcherAt(LATER, Duration.ofMinutes(2));

        assertThat(restarted.markSilentEdges()).isZero();
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isZero();

        // Once the grace has passed, the same stored reports show who is silent.
        var afterGrace = new EdgeLinkWatcher(edges, events, assets, notifications,
                Clock.fixed(LATER.plus(Duration.ofMinutes(2)), ZoneOffset.UTC), new SimpleMeterRegistry(), AFTER, Duration.ZERO);
        assertThat(afterGrace.markSilentEdges()).isEqualTo(10);
    }

    @Test
    void theEdgesThatLeftTheirHomeAreForgottenAndNotAnnouncedAndTheOthersAre() {
        // The edge 5 was replaced: Assets no longer has it in any installation.
        when(assets.fetchReportingEdge(5L)).thenReturn(java.util.Optional.empty());

        int marked = watcherAt(LATER, Duration.ZERO).markSilentEdges();

        assertThat(marked).isEqualTo(9);
        assertThat(edges.findByEdgeId(5L)).isEmpty();
        assertThat(mongo.count(new Query(), DeviceEventDocument.class)).isEqualTo(9);
        assertThat(mongo.findAll(DeviceEventDocument.class)).noneMatch(d -> d.toDomain().edgeId() == 5L);
        // Nothing is left of it to look at: the next run does nothing.
        assertThat(watcherAt(LATER.plusSeconds(30), Duration.ZERO).markSilentEdges()).isZero();
    }
}
