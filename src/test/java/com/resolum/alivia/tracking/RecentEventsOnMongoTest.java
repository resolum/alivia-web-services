package com.resolum.alivia.tracking;

import com.mongodb.ExplainVerbosity;
import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.shared.infrastructure.persistence.mongo.MongoTypeMappingConfiguration;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceEventRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceEventDocument;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The recent events of a home over a real MongoDB: newest first, only that home, and through the index. */
@DataMongoTest
@Import({DeviceEventRepositoryImpl.class, MongoTypeMappingConfiguration.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class RecentEventsOnMongoTest {

    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();
    private static final ReportingContext HOME = new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L));
    private static final ReportingContext OTHER = new ReportingContext(4L, 40L, 400L, Set.of(10L));

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

    @BeforeEach
    void empty() {
        mongo.remove(new Query(), DeviceEventDocument.class);
    }

    private void state(ReportingContext home, String id, Instant at, long device) {
        events.insert(DeviceEvent.fromEdge(home, new ReportedEvent(id, "DEVICE_STATE_CHANGED", at, device, null, null, null,
                null, null, null, Map.of("state", "open")), at.plusSeconds(1), false, POLICY));
    }

    @Test
    void theLatestEventsOfTheHomeComeNewestFirstWhateverTheirDeviceOrNone() {
        state(HOME, "a", T0, 10);
        state(HOME, "b", T0.plusSeconds(30), 11);
        events.insert(DeviceEvent.byServer("dc-edge-3-1", EventType.EDGE_DISCONNECTED, 3L, 30L, 300L, null,
                T0.plusSeconds(60), "Sin reportes"));
        events.insert(DeviceEvent.fromEdge(HOME, new ReportedEvent("help", "HELP_REQUESTED", T0.plusSeconds(45), null, null,
                null, null, null, null, "ayuda", null), T0.plusSeconds(46), false, POLICY));

        var recent = events.findRecentByHousehold(300L, 10);

        assertThat(recent).extracting(DeviceEvent::eventId).containsExactly("dc-edge-3-1", "help", "b", "a");
        assertThat(recent.get(0).deviceId()).isNull();
    }

    @Test
    void theLimitCutsTheListAndAHomeNeverReadsAnothersEvents() {
        for (int i = 0; i < 30; i++) {
            state(HOME, "mine-" + i, T0.plusSeconds(i), 10);
            state(OTHER, "other-" + i, T0.plusSeconds(i), 10);
        }

        var recent = events.findRecentByHousehold(300L, 20);

        assertThat(recent).hasSize(20).allMatch(e -> e.householdId() == 300L);
        assertThat(recent.get(0).eventId()).isEqualTo("mine-29");
        assertThat(recent.get(19).eventId()).isEqualTo("mine-10");
        assertThat(events.findRecentByHousehold(999L, 20)).isEmpty();
    }

    @Test
    void eventsOfTheSameInstantComeInTheSameOrderEveryTime() {
        for (int i = 0; i < 40; i++) state(HOME, "tie-" + i, T0, 10);

        var first = events.findRecentByHousehold(300L, 25).stream().map(DeviceEvent::eventId).toList();
        var second = events.findRecentByHousehold(300L, 25).stream().map(DeviceEvent::eventId).toList();

        assertThat(first).isEqualTo(second).doesNotHaveDuplicates();
    }

    @Test
    void theSearchGoesThroughTheIndexOfTheHomeWithNoSortInMemory() {
        var all = new ArrayList<DeviceEventDocument>();
        for (int i = 0; i < 500; i++) {
            all.add(DeviceEventDocument.from(DeviceEvent.fromEdge(HOME, new ReportedEvent("bulk-" + i, "DEVICE_STATE_CHANGED",
                    T0.plusSeconds(i), 10L, null, null, null, null, null, null, Map.of()), T0.plusSeconds(i + 1), false, POLICY)));
        }
        mongo.insert(all, DeviceEventDocument.class);

        var plan = mongo.getDb().getCollection("device_events").find(new Document("householdId", 300L))
                .sort(new Document("occurredAt", -1).append("_id", -1)).limit(20)
                .explain(ExplainVerbosity.EXECUTION_STATS);

        var winning = ((Document) ((Document) plan.get("queryPlanner")).get("winningPlan")).toJson();
        assertThat(winning).contains("IXSCAN").contains("household_time_id").doesNotContain("COLLSCAN")
                .doesNotContain("\"stage\": \"SORT\"");
        var examined = ((Document) plan.get("executionStats")).get("totalDocsExamined", Number.class).intValue();
        assertThat(examined).isLessThanOrEqualTo(20);
    }
}
