package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeSyncCommandServiceImpl;
import com.resolum.alivia.assets.domain.exceptions.AppliedVersionAhead;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.aggregates.EdgeSyncState;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.AcknowledgeEdgeVersionCommand;
import com.resolum.alivia.assets.domain.model.commands.RecordEdgeSeenCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** What the server keeps of each edge: the version it confirmed and when it was heard. */
class EdgeSyncCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private EdgeSyncStateRepository syncStates;
    private HouseholdDeviceRegistryRepository registries;
    private EdgeSyncCommandServiceImpl service;
    private HouseholdDeviceRegistry registry;

    @BeforeEach
    void setUp() {
        syncStates = mock(EdgeSyncStateRepository.class);
        registries = mock(HouseholdDeviceRegistryRepository.class);
        service = new EdgeSyncCommandServiceImpl(syncStates, registries, CLOCK);
        registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        ReflectionTestUtils.setField(registry, "tableVersion", 12L);
        when(registries.findByAssignedEdgeId(7L)).thenReturn(Optional.of(registry));
    }

    private static EdgeSyncState stored(Long installationId, Long applied, LocalDateTime ackedAt, LocalDateTime seen) {
        try {
            var constructor = EdgeSyncState.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            var state = constructor.newInstance();
            ReflectionTestUtils.setField(state, "edgeId", 7L);
            ReflectionTestUtils.setField(state, "installationId", installationId);
            ReflectionTestUtils.setField(state, "appliedVersion", applied);
            ReflectionTestUtils.setField(state, "ackedAt", ackedAt);
            ReflectionTestUtils.setField(state, "lastSeenAt", seen);
            return state;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    // --- confirming a version ---

    @Test
    void confirmingAVersionStoresItAndAnswersWithWhatIsStored() {
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 10L, NOW, NOW)));

        var result = service.handle(new AcknowledgeEdgeVersionCommand(7L, 10L));

        assertEquals(10L, result.appliedVersion());
        assertEquals(NOW, result.ackedAt());
        var order = inOrder(syncStates);
        order.verify(syncStates).insertIfAbsent(7L, 3L, NOW);
        order.verify(syncStates).resetIfOtherInstallation(7L, 3L);
        order.verify(syncStates).resetIfAheadOfTable(7L, 12L);
        order.verify(syncStates).raiseAppliedVersion(7L, 10L, NOW);
        order.verify(syncStates).touchLastSeen(7L, NOW, NOW.minusMinutes(1));
        order.verify(syncStates).findById(7L);
    }

    @Test
    void aLowerOrRepeatedReportAnswersWithTheHigherVersionThatIsStored() {
        // The statement that raises the version changed nothing: the stored one is higher.
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 11L, NOW.minusHours(1), NOW)));

        var result = service.handle(new AcknowledgeEdgeVersionCommand(7L, 5L));

        assertEquals(11L, result.appliedVersion());
        assertEquals(NOW.minusHours(1), result.ackedAt());
    }

    @Test
    void theCurrentVersionCanBeConfirmedButNotOneThatDoesNotExistYet() {
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 12L, NOW, NOW)));

        assertEquals(12L, service.handle(new AcknowledgeEdgeVersionCommand(7L, 12L)).appliedVersion());

        assertThrows(AppliedVersionAhead.class, () -> service.handle(new AcknowledgeEdgeVersionCommand(7L, 13L)));
        verify(syncStates, times(1)).raiseAppliedVersion(anyLong(), anyLong(), any());
    }

    @Test
    void versionZeroIsAValidConfirmation() {
        ReflectionTestUtils.setField(registry, "tableVersion", 0L);
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 0L, NOW, NOW)));

        assertEquals(0L, service.handle(new AcknowledgeEdgeVersionCommand(7L, 0L)).appliedVersion());
    }

    @Test
    void anEdgeWithoutAnActiveInstallationConfirmsNothing() {
        when(registries.findByAssignedEdgeId(8L)).thenReturn(Optional.empty());
        assertThrows(EdgeWithoutInstallation.class, () -> service.handle(new AcknowledgeEdgeVersionCommand(8L, 1L)));

        ReflectionTestUtils.setField(registry, "status", RegistryStatus.DEACTIVATED);
        assertThrows(EdgeWithoutInstallation.class, () -> service.handle(new AcknowledgeEdgeVersionCommand(7L, 1L)));

        verifyNoInteractions(syncStates);
    }

    @Test
    void theCommandsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new AcknowledgeEdgeVersionCommand(7L, -1L));
        assertThrows(IllegalArgumentException.class, () -> new AcknowledgeEdgeVersionCommand(7L, null));
        assertThrows(IllegalArgumentException.class, () -> new AcknowledgeEdgeVersionCommand(0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RecordEdgeSeenCommand(null, 3L));
        assertThrows(IllegalArgumentException.class, () -> new RecordEdgeSeenCommand(7L, 0L));
    }

    // --- being heard ---

    @Test
    void anEdgeHeardAMomentAgoIsNotWrittenAgain() {
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 5L, NOW, NOW.minusSeconds(30))));

        service.handle(new RecordEdgeSeenCommand(7L, 3L));

        verify(syncStates, never()).insertIfAbsent(anyLong(), anyLong(), any());
        verify(syncStates, never()).touchLastSeen(anyLong(), any(), any());
        verify(syncStates, never()).resetIfOtherInstallation(anyLong(), anyLong());
    }

    @Test
    void anEdgeNotHeardForAMinuteIsRecorded() {
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 5L, NOW, NOW.minusMinutes(2))));

        service.handle(new RecordEdgeSeenCommand(7L, 3L));

        verify(syncStates).touchLastSeen(7L, NOW, NOW.minusMinutes(1));
    }

    @Test
    void anEdgeHeardForTheFirstTimeGetsItsRow() {
        when(syncStates.findById(7L)).thenReturn(Optional.empty());

        service.handle(new RecordEdgeSeenCommand(7L, 3L));

        verify(syncStates).insertIfAbsent(7L, 3L, NOW);
        verify(syncStates).touchLastSeen(7L, NOW, NOW.minusMinutes(1));
    }

    @Test
    void aRowThatNeverRecordedASightingOrBelongsToAnotherInstallationIsCompleted() {
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, null, null, null)));
        service.handle(new RecordEdgeSeenCommand(7L, 3L));
        verify(syncStates).touchLastSeen(7L, NOW, NOW.minusMinutes(1));

        clearInvocations(syncStates);
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(99L, 5L, NOW, NOW)));
        service.handle(new RecordEdgeSeenCommand(7L, 3L));
        verify(syncStates).resetIfOtherInstallation(7L, 3L);
    }

    @Test
    void whatTheEdgeConfirmedBeforeARestoreIsForgottenInsteadOfKeptAsAMaximum() {
        // The database went back to version 10 but the stored confirmation says 40: it can no longer be true.
        ReflectionTestUtils.setField(registry, "tableVersion", 10L);
        when(syncStates.findById(7L)).thenReturn(Optional.of(stored(3L, 8L, NOW, NOW)));

        service.handle(new AcknowledgeEdgeVersionCommand(7L, 8L));

        var order = inOrder(syncStates);
        order.verify(syncStates).resetIfAheadOfTable(7L, 10L);
        order.verify(syncStates).raiseAppliedVersion(7L, 8L, NOW);
    }
}
