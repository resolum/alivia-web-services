package com.resolum.alivia.tracking;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.tracking.application.acl.TrackingContextFacadeImpl;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.queryservices.AutonomyQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.queries.GetAutonomySummaryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The autonomy summary Tracking gives to analytics: what is counted, what it says about the link, and how it fails. */
class AutonomySummaryTest {

    private static final long HOME = 30L;
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-25T00:00:00Z");
    private static final Instant T = Instant.parse("2026-11-24T15:00:00Z");

    private final DeviceEventRepository events = mock(DeviceEventRepository.class);
    private final EdgeStatusRepository edges = mock(EdgeStatusRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AssetsContextFacade assetsFacade = mock(AssetsContextFacade.class);
    private final TrackingContextFacade facade = new TrackingContextFacadeImpl(
            new AutonomyQueryServiceImpl(events, edges, new ExternalAssetsService(assetsFacade), meters));

    @BeforeEach
    void noEvents() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of());
        // The home has the edges 2 and 3.
        when(assetsFacade.fetchEdgesOfHousehold(HOME)).thenReturn(List.of(
                new AssetsContextFacade.AssignedEdge(2L, "MICROPHONE"), new AssetsContextFacade.AssignedEdge(3L, "ACTUATOR")));
    }

    private void givenFacts(CommandFact... facts) {
        doAnswer((InvocationOnMock call) -> {
            Consumer<CommandFact> action = call.getArgument(4);
            for (var fact : facts) action.accept(fact);
            return null;
        }).when(events).forEachCommandFact(anyLong(), any(), any(), any(), any());
    }

    private static CommandFact fact(String command, EventType type, EventResult result, EventOrigin origin, Long device) {
        return new CommandFact(command, type, result, origin, T, device);
    }

    @Test
    void theCommandsAreCountedOnceAndAnswerWithPrimitives() {
        var request = fact("c1", EventType.COMMAND_REQUESTED, null, EventOrigin.LIVE, 10L);
        var done = fact("c1", EventType.COMMAND_RESULT, EventResult.SUCCESS, EventOrigin.SYNCED, 10L);
        givenFacts(request, done, request, done);

        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.householdId()).isEqualTo(HOME);
        assertThat(summary.deviceId()).isNull();
        assertThat(summary.from()).isEqualTo(FROM);
        assertThat(summary.to()).isEqualTo(TO);
        assertThat(summary.total()).isEqualTo(new TrackingContextFacade.AutonomyCounts(1, 1, 0, 0, 0, 0, 0, 1));
        assertThat(summary.byDevice()).hasSize(1);
        assertThat(summary.byDevice().get(0).deviceId()).isEqualTo(10L);
        assertThat(summary.withoutDevice().requested()).isZero();
    }

    @Test
    void thePeriodAndTheDeviceAreThoseOfTheRequest() {
        facade.summarizeAutonomy(HOME, 10L, FROM, TO);

        verify(events).forEachCommandFact(org.mockito.ArgumentMatchers.eq(HOME), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(FROM), org.mockito.ArgumentMatchers.eq(TO), any());
    }

    @Test
    void aPeriodWithoutCommandsIsAllZerosNotAnError() {
        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.total()).isEqualTo(new TrackingContextFacade.AutonomyCounts(0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(summary.byDevice()).isEmpty();
    }

    @Test
    void anEdgeThatIsNotHeardGivesTheDateOfTheLastUpdateAndSaysEventsMayBePending() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(
                new EdgeStatus(3L, 33L, HOME, T, EdgeLink.OFFLINE, 0, T.plusSeconds(200)),
                new EdgeStatus(2L, 32L, HOME, T.plusSeconds(500), EdgeLink.ONLINE, 0, null)));

        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.edgeLink()).isEqualTo("OFFLINE");
        assertThat(summary.lastUpdatedAt()).isEqualTo(T.plusSeconds(500));
        assertThat(summary.mayHavePendingEvents()).isTrue();
    }

    @Test
    void anEdgeThatIsHeardAndHasNothingToSendMeansTheCountsAreUpToDate() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(new EdgeStatus(3L, 33L, HOME, T, EdgeLink.ONLINE, 0, null)));

        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.edgeLink()).isEqualTo("ONLINE");
        assertThat(summary.mayHavePendingEvents()).isFalse();
    }

    @Test
    void aHomeWhoseEdgeNeverReportedIsUnknownWithoutADate() {
        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.edgeLink()).isEqualTo("UNKNOWN");
        assertThat(summary.lastUpdatedAt()).isNull();
        assertThat(summary.mayHavePendingEvents()).isFalse();
    }

    @Test
    void ifTheEventsCannotBeReadItIsNotAnEmptySummary() {
        doThrow(new IllegalStateException("MongoDB is down")).when(events)
                .forEachCommandFact(anyLong(), any(), any(), any(), any());

        assertThatThrownBy(() -> facade.summarizeAutonomy(HOME, null, FROM, TO))
                .isInstanceOf(TrackingStorageUnavailable.class);
        assertThat(meters.get("tracking.storage.failures").tag("operation", "read_autonomy").counter().count()).isEqualTo(1.0);
    }

    @Test
    void aRequestWithoutHouseholdOrWithABackwardsPeriodIsAProgrammingError() {
        assertThatThrownBy(() -> facade.summarizeAutonomy(null, null, FROM, TO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.summarizeAutonomy(HOME, null, null, TO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.summarizeAutonomy(HOME, null, TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new GetAutonomySummaryQuery(HOME, null, FROM, FROM).from()).isEqualTo(FROM);
    }

    @Test
    void anEdgeThatLeftTheHomeDoesNotMakeTheLinkOfTheHomeOfflineNorGivePendingEvents() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(
                new EdgeStatus(9L, 39L, HOME, T, EdgeLink.OFFLINE, 5, T.plusSeconds(200)),
                new EdgeStatus(3L, 33L, HOME, T.plusSeconds(60), EdgeLink.ONLINE, 0, null)));

        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.edgeLink()).isEqualTo("ONLINE");
        assertThat(summary.lastUpdatedAt()).isEqualTo(T.plusSeconds(60));
        assertThat(summary.mayHavePendingEvents()).isFalse();
    }

    @Test
    void aHomeThatHasNoEdgeAnymoreIsUnknownWhateverIsLeftInTheDatabase() {
        when(assetsFacade.fetchEdgesOfHousehold(HOME)).thenReturn(List.of());
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(new EdgeStatus(9L, 39L, HOME, T, EdgeLink.OFFLINE, 0, T)));

        var summary = facade.summarizeAutonomy(HOME, null, FROM, TO);

        assertThat(summary.edgeLink()).isEqualTo("UNKNOWN");
        assertThat(summary.lastUpdatedAt()).isNull();
    }
}
