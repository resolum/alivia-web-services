package com.resolum.alivia.assets.application.internal.outboundservices.edge;

import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent.Cause;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetTableVersionByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The wait of an edge that is up to date: what wakes it, what ends it and what it never does. The work runs in the
 * calling thread here, so every step is deterministic.
 */
class LongPollEdgeSynchronizationServiceTest {

    private static final Duration LONG = Duration.ofMinutes(5);

    private HouseholdDeviceRegistryQueryService queries;
    private LongPollEdgeSynchronizationService service;

    @BeforeEach
    void setUp() {
        queries = mock(HouseholdDeviceRegistryQueryService.class);
        // Direct executor and no scheduler: the test calls the periodic check itself.
        service = new LongPollEdgeSynchronizationService(queries, Runnable::run, null, Duration.ofSeconds(2));
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(12L));
    }

    private static EdgeAccessTable table(long version) {
        return new EdgeAccessTable(3L, 5L, version, List.of());
    }

    private static EdgeSyncMessage changeOf(long installation) {
        return new EdgeSyncMessage(Cause.DEVICE_CHANGED, installation, 5L, 1L, table(13L));
    }

    private CompletableFuture<Optional<EdgeAccessTable>> await(long edge, long installation, long known) {
        return service.awaitNewerTable(edge, installation, known, LONG);
    }

    @Test
    void anEdgeThatIsUpToDateWaitsWhileThereIsNothingNewer() {
        var future = await(7L, 3L, 12L);

        assertFalse(future.isDone());
        verify(queries, never()).handle(any(GetEdgeAccessTableByEdgeIdQuery.class));
        assertEquals(1, service.waiting());
    }

    @Test
    void theChangeOfItsInstallationWakesItWithTheNewTable() throws Exception {
        var future = await(7L, 3L, 12L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(13L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(table(13L)));

        service.publish(changeOf(3L));

        assertEquals(13L, future.get(1, TimeUnit.SECONDS).orElseThrow().version());
        assertEquals(0, service.waiting());
    }

    @Test
    void theChangeOfAnotherInstallationDoesNotWakeIt() {
        var future = await(7L, 3L, 12L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(9L))).thenReturn(Optional.of(50L));

        service.publish(changeOf(9L));

        assertFalse(future.isDone());
        verify(queries, never()).handle(new GetTableVersionByInstallationIdQuery(9L));
    }

    @Test
    void theNextPeriodicCheckWakesItWhenTheChangeWasMadeByAnotherInstance() throws Exception {
        var future = await(7L, 3L, 12L);
        service.checkAll();
        assertFalse(future.isDone(), "nothing changed yet");

        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(14L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(table(14L)));
        service.checkAll();

        assertEquals(14L, future.get(1, TimeUnit.SECONDS).orElseThrow().version());
    }

    @Test
    void aChangeBetweenReadingTheVersionAndWaitingIsFoundAtOnce() throws Exception {
        // The caller saw version 12, but 13 was committed before the wait was registered.
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(13L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(table(13L)));

        var future = await(7L, 3L, 12L);

        assertEquals(13L, future.get(1, TimeUnit.SECONDS).orElseThrow().version());
    }

    @Test
    void oneCheckReadsOneVersionForAllTheEdgesOfTheInstallationAndOnlyTheOnesBehindReadTheirTable() throws Exception {
        var mic = await(7L, 3L, 12L);
        var actuator = await(8L, 3L, 13L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(13L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(table(13L)));
        clearInvocations(queries);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(13L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(table(13L)));

        service.publish(changeOf(3L));

        assertTrue(mic.isDone());
        assertFalse(actuator.isDone(), "the actuator edge already has version 13");
        verify(queries, times(1)).handle(new GetTableVersionByInstallationIdQuery(3L));
        verify(queries, never()).handle(new GetEdgeAccessTableByEdgeIdQuery(8L));
    }

    @Test
    void theTimeLimitEndsTheWaitWithNothingAndRemovesIt() throws Exception {
        var future = service.awaitNewerTable(7L, 3L, 12L, Duration.ofMillis(50));

        assertTrue(future.get(2, TimeUnit.SECONDS).isEmpty());
        assertEquals(0, service.waiting());
    }

    @Test
    void aNewerWaitOfTheSameEdgeEndsThePreviousOne() throws Exception {
        var first = await(7L, 3L, 12L);

        var second = await(7L, 3L, 12L);

        assertTrue(first.get(1, TimeUnit.SECONDS).isEmpty());
        assertFalse(second.isDone());
        assertEquals(1, service.waiting());
    }

    @Test
    void theEdgeIsToldWhenItsInstallationStoppedBeingActive() {
        var future = await(7L, 3L, 12L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.empty());

        service.publish(changeOf(3L));

        var failure = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertInstanceOf(EdgeWithoutInstallation.class, failure.getCause());
    }

    @Test
    void theEdgeIsToldWhenItWasRemovedFromItsInstallation() {
        var future = await(7L, 3L, 12L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(13L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.empty());

        service.publish(changeOf(3L));

        var failure = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertInstanceOf(EdgeWithoutInstallation.class, failure.getCause());
    }

    @Test
    void aFailingDatabaseNeverEndsTheWaitAndTheNextCheckTriesAgain() throws Exception {
        var future = await(7L, 3L, 12L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> service.publish(changeOf(3L)));
        assertDoesNotThrow(() -> service.checkAll());
        assertFalse(future.isDone());

        doReturn(Optional.of(13L)).when(queries).handle(new GetTableVersionByInstallationIdQuery(3L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenThrow(new IllegalStateException("still down"));
        service.checkAll();
        assertFalse(future.isDone());

        doReturn(Optional.of(table(13L))).when(queries).handle(new GetEdgeAccessTableByEdgeIdQuery(7L));
        service.checkAll();
        assertEquals(13L, future.get(1, TimeUnit.SECONDS).orElseThrow().version());
    }

    @Test
    void aTableThatIsNotNewerThanWhatTheEdgeHasIsNeverDelivered() {
        var future = await(7L, 3L, 12L);
        when(queries.handle(new GetTableVersionByInstallationIdQuery(3L))).thenReturn(Optional.of(13L));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(table(12L)));

        service.publish(changeOf(3L));

        assertFalse(future.isDone());
    }

    @Test
    void aChangeWithNobodyWaitingReadsNothing() {
        service.publish(changeOf(3L));

        verifyNoInteractions(queries);
    }

    @Test
    void shuttingDownEndsEveryWaitWithNothing() throws Exception {
        when(queries.handle(new GetTableVersionByInstallationIdQuery(4L))).thenReturn(Optional.of(1L));
        var one = await(7L, 3L, 12L);
        var two = await(8L, 4L, 1L);

        service.stop();

        assertTrue(one.get(1, TimeUnit.SECONDS).isEmpty());
        assertTrue(two.get(1, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void thePollIntervalMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new LongPollEdgeSynchronizationService(queries, Runnable::run, null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LongPollEdgeSynchronizationService(queries, Runnable::run, null, Duration.ofSeconds(-1)));
    }

    // --- the wait of an edge ends when its credential is revoked or replaced ---

    @Test
    void endingTheWaitOfAnEdgeAnswersItWithNothingAndLeavesTheOthersWaiting() throws Exception {
        var revoked = await(7L, 3L, 12L);
        var other = await(8L, 3L, 12L);

        service.endWaitOf(7L);

        assertTrue(revoked.get(1, TimeUnit.SECONDS).isEmpty());
        assertFalse(other.isDone());
        assertEquals(1, service.waiting());
    }

    @Test
    void endingTheWaitOfAnEdgeThatIsNotWaitingDoesNothing() {
        var waiting = await(8L, 3L, 12L);

        assertDoesNotThrow(() -> service.endWaitOf(7L));

        assertFalse(waiting.isDone());
    }

    @Test
    void insideATransactionTheWaitEndsOnlyWhenItCommits() throws Exception {
        var waiting = await(7L, 3L, 12L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.endWaitOf(7L);
            assertFalse(waiting.isDone(), "the change is not visible yet: the edge could start a new wait with the old credential");

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertTrue(waiting.get(1, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void ifTheTransactionRollsBackTheCredentialDidNotChangeAndTheWaitGoesOn() {
        var waiting = await(7L, 3L, 12L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.endWaitOf(7L);
            // A rollback calls afterCompletion, never afterCommit.
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertFalse(waiting.isDone());
    }
}
