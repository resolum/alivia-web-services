package com.resolum.alivia.tracking;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade.ScopedDevice;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.queryservices.HomeStatusQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.queries.GetHomeStatusByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeStatusView;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.repositories.DeviceStateRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What the family sees of a home: its devices with their last known state, and how old that state is. */
class HomeStatusQueryServiceTest {

    private static final long USER = 7L;
    private static final long PERSON = 2L;
    private static final long HOME = 30L;
    private static final Instant T = Instant.parse("2026-11-24T15:00:00Z");

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final AssetsContextFacade facade = mock(AssetsContextFacade.class);
    private final DeviceStateRepository states = mock(DeviceStateRepository.class);
    private final EdgeStatusRepository edges = mock(EdgeStatusRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final HomeStatusQueryServiceImpl service = new HomeStatusQueryServiceImpl(access,
            new ExternalAssetsService(facade), states, edges, meters);

    @BeforeEach
    void home() {
        when(access.requireReadableHouseholdOfPerson(USER, PERSON)).thenReturn(HOME);
        when(facade.fetchDevicesOfHousehold(HOME)).thenReturn(List.of(
                new ScopedDevice(12L, "Cortina", "CURTAIN", "IN_USE", "cuarto"),
                new ScopedDevice(10L, "Puerta", "DOOR", "IN_USE", "entrada"),
                new ScopedDevice(11L, "Luz", "LIGHT", "IN_USE", "sala")));
        when(states.findAllByDeviceIds(anyCollection())).thenReturn(List.of());
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of());
        // The home has the edges 2 and 3.
        when(facade.fetchEdgesOfHousehold(HOME)).thenReturn(List.of(new AssetsContextFacade.AssignedEdge(2L, "MICROPHONE"),
                new AssetsContextFacade.AssignedEdge(3L, "ACTUATOR")));
    }

    private static DeviceState state(long device, long edge, long household, ConnectionStatus connection,
                                     OperationalStatus operational, String current, Integer battery, Instant observedAt) {
        return new DeviceState(device, edge, household, connection, operational, current, battery, observedAt, observedAt,
                observedAt.plusSeconds(1), List.of());
    }

    private static EdgeStatus edge(long id, Instant lastReport, EdgeLink link, int pending, Instant offlineSince) {
        return new EdgeStatus(id, 30L + id, HOME, lastReport, link, pending, offlineSince);
    }

    private HomeStatusView ask() {
        return service.handle(new GetHomeStatusByAssistedPersonQuery(USER, PERSON));
    }

    @Test
    void everyDeviceOfTheHomeIsListedByIdAndOneThatNeverReportedIsUnknown() {
        var view = ask();

        assertThat(view.householdId()).isEqualTo(HOME);
        assertThat(view.devices()).extracting(d -> d.deviceId()).containsExactly(10L, 11L, 12L);
        assertThat(view.devices()).allSatisfy(device -> {
            assertThat(device.connectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
            assertThat(device.operationalStatus()).isNull();
            assertThat(device.lastUpdatedAt()).isNull();
            assertThat(device.stale()).isFalse();
        });
        assertThat(view.devices().get(0).alias()).isEqualTo("Puerta");
        assertThat(view.link()).isEqualTo(HomeLink.UNKNOWN);
        assertThat(view.lastUpdatedAt()).isNull();
        assertThat(view.mayHavePendingEvents()).isFalse();
    }

    @Test
    void listsTheEdgesOfTheHomeWithWhetherEachOneIsHeard() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(edge(3L, T, EdgeLink.ONLINE, 0, null)));

        var view = ask();

        assertThat(view.edges()).extracting(e -> e.edgeId() + ":" + e.kind() + ":" + e.link())
                .containsExactly("2:MICROPHONE:UNKNOWN", "3:ACTUATOR:ONLINE");
        assertThat(view.edges().get(0).lastReportAt()).isNull();
        assertThat(view.edges().get(1).lastReportAt()).isEqualTo(T);
    }

    @Test
    void aDeviceOfAnEdgeThatIsHeardShowsItsStateAndIsNotStale() {
        when(states.findAllByDeviceIds(anyCollection())).thenReturn(List.of(
                state(11L, 3L, HOME, ConnectionStatus.CONNECTED, OperationalStatus.DEGRADED, "on", 15, T)));
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(edge(3L, T.plusSeconds(20), EdgeLink.ONLINE, 0, null)));

        var view = ask();

        var light = view.devices().get(1);
        assertThat(light.currentState()).isEqualTo("on");
        assertThat(light.batteryPct()).isEqualTo(15);
        assertThat(light.operationalStatus()).isEqualTo(OperationalStatus.DEGRADED);
        assertThat(light.stale()).isFalse();
        assertThat(light.lastUpdatedAt()).isEqualTo(T.plusSeconds(1));
        assertThat(view.link()).isEqualTo(HomeLink.ONLINE);
        assertThat(view.offlineSince()).isNull();
        assertThat(view.lastUpdatedAt()).isEqualTo(T.plusSeconds(20));
    }

    @Test
    void aDeviceOfAnEdgeThatIsNotHeardKeepsItsLastStateMarkedStaleAndIsNotShownDisconnected() {
        when(states.findAllByDeviceIds(anyCollection())).thenReturn(List.of(
                state(10L, 3L, HOME, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", 78, T),
                state(11L, 2L, HOME, ConnectionStatus.CONNECTED, OperationalStatus.OK, "on", null, T.plusSeconds(60))));
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(
                edge(3L, T.plusSeconds(5), EdgeLink.OFFLINE, 0, T.plusSeconds(185)),
                edge(2L, T.plusSeconds(300), EdgeLink.ONLINE, 0, null)));

        var view = ask();

        var door = view.devices().get(0);
        assertThat(door.stale()).isTrue();
        assertThat(door.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(door.currentState()).isEqualTo("closed");
        assertThat(view.devices().get(1).stale()).isFalse();
        assertThat(view.link()).isEqualTo(HomeLink.OFFLINE);
        assertThat(view.offlineSince()).isEqualTo(T.plusSeconds(185));
        assertThat(view.mayHavePendingEvents()).isTrue();
        assertThat(view.lastUpdatedAt()).isEqualTo(T.plusSeconds(300));
    }

    @Test
    void theEarliestSilenceIsWhenTheHomeStoppedBeingHeard() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(
                edge(3L, T, EdgeLink.OFFLINE, 0, T.plusSeconds(600)),
                edge(2L, T, EdgeLink.OFFLINE, 0, T.plusSeconds(200))));

        assertThat(ask().offlineSince()).isEqualTo(T.plusSeconds(200));
    }

    @Test
    void anEdgeThatSaidItHasEventsToSendMeansTheyMayBeWaitingEvenWhileOnline() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(edge(3L, T, EdgeLink.ONLINE, 4, null)));

        var view = ask();

        assertThat(view.link()).isEqualTo(HomeLink.ONLINE);
        assertThat(view.mayHavePendingEvents()).isTrue();
    }

    @Test
    void theStateOfADeviceThatWasInAnotherHomeIsNotShown() {
        when(states.findAllByDeviceIds(anyCollection())).thenReturn(List.of(
                state(10L, 9L, 99L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "open", 50, T)));

        var door = ask().devices().get(0);

        assertThat(door.connectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
        assertThat(door.currentState()).isNull();
    }

    @Test
    void aHomeWithoutDevicesAnswersEmptyAndReadsNoStates() {
        when(facade.fetchDevicesOfHousehold(HOME)).thenReturn(List.of());

        var view = ask();

        assertThat(view.devices()).isEmpty();
        verify(states, never()).findAllByDeviceIds(anyCollection());
    }

    @Test
    void whoMayNotReadThePersonIsRefusedBeforeAnythingIsRead() {
        when(access.requireReadableHouseholdOfPerson(8L, PERSON)).thenThrow(new AssistedPersonNotAccessible(PERSON));

        assertThatThrownBy(() -> service.handle(new GetHomeStatusByAssistedPersonQuery(8L, PERSON)))
                .isInstanceOf(AssistedPersonNotAccessible.class);
        verifyNoInteractions(states, edges);
    }

    @Test
    void aPersonWithoutAHomeIsToldApart() {
        when(access.requireReadableHouseholdOfPerson(USER, 3L)).thenThrow(new AssistedPersonWithoutHousehold(3L));

        assertThatThrownBy(() -> service.handle(new GetHomeStatusByAssistedPersonQuery(USER, 3L)))
                .isInstanceOf(AssistedPersonWithoutHousehold.class);
    }

    @Test
    void ifTheStatesCannotBeReadItIsNotAnEmptyHome() {
        when(states.findAllByDeviceIds(anyCollection())).thenThrow(new IllegalStateException("MongoDB is down"));

        assertThatThrownBy(this::ask).isInstanceOf(TrackingStorageUnavailable.class);
        assertThat(meters.get("tracking.storage.failures").tag("operation", "read_home_status").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void theStatusOfAnEdgeThatLeftTheHomeDoesNotMakeTheHomeOfflineAndItsDevicesAreStale() {
        // The edge 9 was replaced: its status is still in the database, silent for good.
        when(states.findAllByDeviceIds(anyCollection())).thenReturn(List.of(
                state(10L, 9L, HOME, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", 78, T),
                state(11L, 3L, HOME, ConnectionStatus.CONNECTED, OperationalStatus.OK, "on", null, T.plusSeconds(60))));
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(
                edge(9L, T, EdgeLink.OFFLINE, 0, T.plusSeconds(200)),
                edge(3L, T.plusSeconds(120), EdgeLink.ONLINE, 0, null)));

        var view = ask();

        assertThat(view.link()).isEqualTo(HomeLink.ONLINE);
        assertThat(view.offlineSince()).isNull();
        assertThat(view.mayHavePendingEvents()).isFalse();
        // Neither the old edge's report nor the state it left count as news about the home.
        assertThat(view.lastUpdatedAt()).isEqualTo(T.plusSeconds(120));
        // What the old edge last said about the door is still shown, as the last known state, and nobody updates it.
        assertThat(view.devices().get(0).stale()).isTrue();
        assertThat(view.devices().get(0).currentState()).isEqualTo("closed");
        assertThat(view.devices().get(1).stale()).isFalse();
    }

    @Test
    void aHomeWhoseOnlyStatusesAreOfEdgesThatLeftIsUnknownNotOffline() {
        when(edges.findAllByHouseholdId(HOME)).thenReturn(List.of(edge(9L, T, EdgeLink.OFFLINE, 3, T.plusSeconds(200))));

        var view = ask();

        assertThat(view.link()).isEqualTo(HomeLink.UNKNOWN);
        assertThat(view.offlineSince()).isNull();
        assertThat(view.lastUpdatedAt()).isNull();
        assertThat(view.mayHavePendingEvents()).isFalse();
    }

    @Test
    void theEdgesOfTheHomeAreAskedToAssetsEveryTime() {
        ask();

        org.mockito.Mockito.verify(facade).fetchEdgesOfHousehold(HOME);
    }
}
