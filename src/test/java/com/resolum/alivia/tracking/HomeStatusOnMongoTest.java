package com.resolum.alivia.tracking;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade.ScopedDevice;
import com.resolum.alivia.integration.MongoContainerSupport;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.queryservices.HomeStatusQueryServiceImpl;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.queries.GetHomeStatusByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.infrastructure.adapters.DeviceStateRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.adapters.EdgeStatusRepositoryImpl;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceStateDocument;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.EdgeStatusDocument;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The join of the devices of a home with what Mongo keeps, over a real database with several homes in it. */
@DataMongoTest
@Import({DeviceStateRepositoryImpl.class, EdgeStatusRepositoryImpl.class})
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class HomeStatusOnMongoTest {

    private static final Instant T = Instant.parse("2026-11-24T15:00:00Z");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoContainerSupport::connectionString);
        registry.add("spring.mongodb.database", () -> "tracking_" + UUID.randomUUID().toString().replace("-", ""));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private DeviceStateRepositoryImpl states;
    @Autowired
    private EdgeStatusRepositoryImpl edges;
    @Autowired
    private MongoTemplate mongo;

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final AssetsContextFacade facade = mock(AssetsContextFacade.class);

    @BeforeEach
    void twoHomes() {
        mongo.remove(new Query(), DeviceStateDocument.class);
        mongo.remove(new Query(), EdgeStatusDocument.class);
        when(access.requireReadableHouseholdOfPerson(7L, 2L)).thenReturn(30L);
        when(facade.fetchDevicesOfHousehold(30L)).thenReturn(List.of(
                new ScopedDevice(10L, "Puerta", "DOOR", "IN_USE", "entrada"),
                new ScopedDevice(11L, "Luz", "LIGHT", "IN_USE", "sala"),
                new ScopedDevice(12L, "Cortina", "CURTAIN", "IN_USE", "cuarto")));
        edges.recordHeartbeat(new EdgeStatus(3L, 33L, 30L, T, EdgeLink.ONLINE, 2, null));
        edges.recordHeartbeat(new EdgeStatus(4L, 34L, 31L, T, EdgeLink.ONLINE, 0, null));
        states.saveUnlessOlder(state(10L, 3L, 30L, "closed"));
        states.saveUnlessOlder(state(11L, 3L, 30L, "on"));
        // Another home, and a device of this one that was in that other home before it was moved.
        states.saveUnlessOlder(state(50L, 4L, 31L, "open"));
        states.saveUnlessOlder(state(12L, 4L, 31L, "open"));
    }

    private static DeviceState state(long device, long edge, long household, String current) {
        return new DeviceState(device, edge, household, ConnectionStatus.CONNECTED, OperationalStatus.OK, current, 80, T, T,
                T.plusSeconds(1), List.of());
    }

    private HomeStatusQueryServiceImpl service() {
        return new HomeStatusQueryServiceImpl(access, new ExternalAssetsService(facade), states, edges,
                new SimpleMeterRegistry());
    }

    @Test
    void theStatesAndTheEdgesOfThisHomeAreJoinedToItsDevicesAndNothingOfAnotherHome() {
        var view = service().handle(new GetHomeStatusByAssistedPersonQuery(7L, 2L));

        assertThat(view.devices()).extracting(d -> d.deviceId()).containsExactly(10L, 11L, 12L);
        assertThat(view.devices().get(0).currentState()).isEqualTo("closed");
        assertThat(view.devices().get(1).currentState()).isEqualTo("on");
        // The state of the curtain is the one of the home where it used to be: it is not shown here.
        assertThat(view.devices().get(2).connectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
        assertThat(view.link()).isEqualTo(HomeLink.ONLINE);
        assertThat(view.mayHavePendingEvents()).isTrue();
        assertThat(view.lastUpdatedAt()).isEqualTo(T.plusSeconds(1));
    }

    @Test
    void anEdgeThatWentOfflineMarksItsDevicesStale() {
        edges.markOffline(3L, T, T.plusSeconds(200));

        var view = service().handle(new GetHomeStatusByAssistedPersonQuery(7L, 2L));

        assertThat(view.link()).isEqualTo(HomeLink.OFFLINE);
        assertThat(view.offlineSince()).isEqualTo(T.plusSeconds(200));
        assertThat(view.devices().get(0).stale()).isTrue();
        assertThat(view.devices().get(0).currentState()).isEqualTo("closed");
        assertThat(view.devices().get(2).stale()).isFalse();
    }

    @Test
    void theEdgesOfAHomeAreFoundByAnIndexOfItsHousehold() {
        assertThat(edges.findAllByHouseholdId(30L)).extracting(EdgeStatus::edgeId).containsExactly(3L);
        assertThat(edges.findAllByHouseholdId(31L)).extracting(EdgeStatus::edgeId).containsExactly(4L);
        assertThat(edges.findAllByHouseholdId(99L)).isEmpty();

        var indexed = mongo.indexOps(EdgeStatusDocument.class).getIndexInfo().stream().map(IndexInfo::getName).toList();
        assertThat(indexed).contains("household", "link_last_report");
        // The search is answered from the index and does not read the whole collection.
        var plan = mongo.getDb().getCollection("edge_statuses")
                .find(new Document("householdId", 30L)).explain();
        assertThat(plan.toJson()).contains("IXSCAN").doesNotContain("COLLSCAN");
    }
}
