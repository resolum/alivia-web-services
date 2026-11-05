package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.queryservices.EdgeSyncQueryServiceImpl;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.EdgeSyncState;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgeSyncQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdgeSync.EdgeSync;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** How far behind the table each edge service of an installation is. */
class EdgeSyncQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private HouseholdDeviceRegistryRepository registries;
    private EdgeSyncStateRepository syncStates;
    private EdgeSyncQueryServiceImpl service;
    private HouseholdDeviceRegistry registry;

    @BeforeEach
    void setUp() {
        registries = mock(HouseholdDeviceRegistryRepository.class);
        syncStates = mock(EdgeSyncStateRepository.class);
        service = new EdgeSyncQueryServiceImpl(registries, syncStates);
        registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        ReflectionTestUtils.setField(registry, "tableVersion", 12L);
        when(registries.findByInstallationId(new InstallationId(3L))).thenReturn(Optional.of(registry));
    }

    private Edge placed(long id, String type) {
        var edge = new Edge(1L, String.format("AA:BB:CC:DD:EE:%02X", id), type);
        ReflectionTestUtils.setField(edge, "id", id);
        registry.placeEdge(edge);
        return edge;
    }

    private static EdgeSyncState state(long edgeId, Long applied, LocalDateTime ackedAt, LocalDateTime seen) {
        try {
            var constructor = EdgeSyncState.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            var state = constructor.newInstance();
            ReflectionTestUtils.setField(state, "edgeId", edgeId);
            ReflectionTestUtils.setField(state, "installationId", 3L);
            ReflectionTestUtils.setField(state, "appliedVersion", applied);
            ReflectionTestUtils.setField(state, "ackedAt", ackedAt);
            ReflectionTestUtils.setField(state, "lastSeenAt", seen);
            return state;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void showsEachEdgeWithItsConfirmationAndHowFarBehindItIs() {
        placed(1, "MICROPHONE_EDGE");
        placed(2, "ACTUATOR_EDGE");
        when(syncStates.findAllByInstallationId(3L)).thenReturn(List.of(
                state(1, 12L, NOW.minusMinutes(5), NOW.minusSeconds(10)),
                state(2, 9L, NOW.minusHours(1), NOW.minusMinutes(2))));

        var view = service.handle(new GetInstallationEdgeSyncQuery(3L)).orElseThrow();

        assertEquals(12L, view.currentVersion());
        assertEquals(2, view.edges().size());
        var mic = view.edges().stream().filter(e -> e.edgeId() == 1L).findFirst().orElseThrow();
        assertEquals(EdgeType.MICROPHONE_EDGE, mic.edgeType());
        assertEquals(12L, mic.appliedVersion());
        assertEquals(0L, mic.behindBy());
        assertTrue(mic.upToDate());
        assertEquals(NOW.minusSeconds(10), mic.lastSeenAt());
        var actuator = view.edges().stream().filter(e -> e.edgeId() == 2L).findFirst().orElseThrow();
        assertEquals(3L, actuator.behindBy());
        assertFalse(actuator.upToDate());
        assertEquals(NOW.minusHours(1), actuator.ackedAt());
    }

    @Test
    void anEdgeThatNeverConfirmedOrWasNeverHeardShowsNulls() {
        placed(2, "ACTUATOR_EDGE");
        when(syncStates.findAllByInstallationId(3L)).thenReturn(List.of());

        var edge = service.handle(new GetInstallationEdgeSyncQuery(3L)).orElseThrow().edges().get(0);

        assertNull(edge.appliedVersion());
        assertNull(edge.ackedAt());
        assertNull(edge.lastSeenAt());
        assertNull(edge.behindBy());
        assertFalse(edge.upToDate());
    }

    @Test
    void anEdgeThatAskedButNeverConfirmedShowsWhenItWasHeard() {
        placed(2, "ACTUATOR_EDGE");
        when(syncStates.findAllByInstallationId(3L)).thenReturn(List.of(state(2, null, null, NOW)));

        var edge = service.handle(new GetInstallationEdgeSyncQuery(3L)).orElseThrow().edges().get(0);

        assertEquals(NOW, edge.lastSeenAt());
        assertNull(edge.appliedVersion());
        assertFalse(edge.upToDate());
    }

    @Test
    void onlyTheEdgesAssignedToTheInstallationAppear() {
        placed(1, "MICROPHONE_EDGE");
        // A leftover row of an edge that is no longer in the installation is ignored.
        when(syncStates.findAllByInstallationId(3L)).thenReturn(List.of(state(1, 12L, NOW, NOW), state(99, 5L, NOW, NOW)));

        var view = service.handle(new GetInstallationEdgeSyncQuery(3L)).orElseThrow();

        assertEquals(List.of(1L), view.edges().stream().map(EdgeSync::edgeId).toList());
    }

    @Test
    void aInstallationWithoutEdgesHasAnEmptyListAndAnUnknownOneHasNoView() {
        when(syncStates.findAllByInstallationId(3L)).thenReturn(List.of());
        assertTrue(service.handle(new GetInstallationEdgeSyncQuery(3L)).orElseThrow().edges().isEmpty());

        when(registries.findByInstallationId(new InstallationId(9L))).thenReturn(Optional.empty());
        assertTrue(service.handle(new GetInstallationEdgeSyncQuery(9L)).isEmpty());
    }

    @Test
    void behindByNeverGoesNegativeWhenTheEdgeConfirmedMoreThanTheTable() {
        var edge = EdgeSync.of(1L, EdgeType.ACTUATOR_EDGE, 5L, 9L, NOW, NOW);

        assertEquals(0L, edge.behindBy());
        assertTrue(edge.upToDate());
    }
}
