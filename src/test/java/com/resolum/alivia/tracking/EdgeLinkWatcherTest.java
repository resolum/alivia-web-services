package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.application.internal.tasks.EdgeLinkWatcher;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The watcher of the edge links: what it marks, writes and tells, and what a failure or a second run leaves. */
class EdgeLinkWatcherTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:00:00Z");
    private static final Duration AFTER = Duration.ofMinutes(3);

    private static final class MovingClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(T0);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /** Behaves like the Mongo one: the mark is conditional on the status being online and unchanged. */
    private static class InMemoryEdges implements EdgeStatusRepository {
        final Map<Long, EdgeStatus> kept = new LinkedHashMap<>();
        /** What the search returns when it must be stale; {@code null} means the truth. */
        List<EdgeStatus> staleListing;
        int marks;

        @Override
        public Optional<EdgeStatus> findByEdgeId(Long edgeId) {
            return Optional.ofNullable(kept.get(edgeId));
        }

        @Override
        public Optional<EdgeStatus> recordHeartbeat(EdgeStatus status) {
            return Optional.ofNullable(kept.put(status.edgeId(), status));
        }

        @Override
        public synchronized boolean markOffline(Long edgeId, Instant seen, Instant offlineSince) {
            var current = kept.get(edgeId);
            if (current == null || current.link() != EdgeLink.ONLINE || !current.lastReportAt().equals(seen)) return false;
            kept.put(edgeId, new EdgeStatus(edgeId, current.installationId(), current.householdId(), seen,
                    EdgeLink.OFFLINE, current.pendingEvents(), offlineSince));
            marks++;
            return true;
        }

        @Override
        public List<EdgeStatus> findAllByHouseholdId(Long householdId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EdgeStatus> findOnlineSilentSince(Instant limit) {
            if (staleListing != null) return staleListing;
            return kept.values().stream()
                    .filter(s -> s.link() == EdgeLink.ONLINE && s.lastReportAt().isBefore(limit)).toList();
        }
    }

    private static final class InMemoryEvents implements DeviceEventRepository {
        final Map<String, DeviceEvent> kept = new HashMap<>();
        boolean failing;

        @Override
        public EventInsertion insert(DeviceEvent event) {
            if (failing) throw new IllegalStateException("MongoDB is down");
            return kept.putIfAbsent(event.edgeId() + "/" + event.eventId(), event) == null
                    ? EventInsertion.INSERTED : EventInsertion.DUPLICATE;
        }

        @Override
        public Optional<DeviceEvent> findByEdgeAndEventId(Long edgeId, String eventId) {
            return Optional.ofNullable(kept.get(edgeId + "/" + eventId));
        }

        @Override
        public EventPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, EventType type, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachCommandFact(Long householdId, Long deviceId, Instant from, Instant to,
                java.util.function.Consumer<com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact> action) {
            throw new UnsupportedOperationException();
        }


        @Override
        public List<DeviceEvent> findRecentByHousehold(Long householdId, int limit) {
            throw new UnsupportedOperationException();
        }

        List<DeviceEvent> ofType(EventType type) {
            return new ArrayList<>(kept.values().stream().filter(e -> e.type() == type).toList());
        }
    }

    private final MovingClock clock = new MovingClock();
    private final InMemoryEdges edges = new InMemoryEdges();
    private final InMemoryEvents events = new InMemoryEvents();
    private final ExternalNotificationsService notifications = mock(ExternalNotificationsService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @BeforeEach
    void relatives() {
        when(notifications.relativesOf(300L)).thenReturn(Set.of(8L));
        when(notifications.relativesOf(301L)).thenReturn(Set.of(9L));
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    private EdgeLinkWatcher watcher(Duration grace) {
        return new EdgeLinkWatcher(edges, events, notifications, clock, meters, AFTER, grace);
    }

    private void online(long edgeId, long householdId, Instant lastReport) {
        edges.kept.put(edgeId, new EdgeStatus(edgeId, 30L + edgeId, householdId, lastReport, EdgeLink.ONLINE, 0, null));
    }

    @Test
    void anEdgeSilentForLongerThanAllowedIsMarkedWrittenAndNotifiedOnce() {
        online(3L, 300L, T0);
        var watcher = watcher(Duration.ZERO);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));

        assertThat(watcher.markSilentEdges()).isEqualTo(1);

        var status = edges.kept.get(3L);
        assertThat(status.link()).isEqualTo(EdgeLink.OFFLINE);
        assertThat(status.offlineSince()).isEqualTo(clock.instant());
        assertThat(status.lastReportAt()).isEqualTo(T0);
        var written = events.ofType(EventType.EDGE_DISCONNECTED);
        assertThat(written).hasSize(1);
        assertThat(written.get(0).householdId()).isEqualTo(300L);
        assertThat(written.get(0).eventId()).isEqualTo("dc-edge-3-" + T0.toEpochMilli());
        verify(notifications).notifyOnce(8L, "EDGE_DISCONNECTED", "dc-edge-3-" + T0.toEpochMilli(), "Casa sin conexión",
                "Un equipo de la casa dejó de reportar. Lo que ves es lo último que recibimos y pueden existir eventos pendientes.");
        assertThat(meters.get("tracking.link.transitions").tag("to", "offline").counter().count()).isEqualTo(1.0);
    }

    @Test
    void aSecondRunLeavesEverythingAsItIs() {
        online(3L, 300L, T0);
        var watcher = watcher(Duration.ZERO);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));
        watcher.markSilentEdges();

        clock.now.set(T0.plus(AFTER).plusSeconds(60));
        assertThat(watcher.markSilentEdges()).isZero();

        assertThat(events.ofType(EventType.EDGE_DISCONNECTED)).hasSize(1);
        verify(notifications, times(1)).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
        assertThat(edges.marks).isEqualTo(1);
    }

    @Test
    void anEdgeWithinTheAllowedSilenceIsNotTouched() {
        online(3L, 300L, T0);
        var watcher = watcher(Duration.ZERO);

        clock.now.set(T0.plus(AFTER));
        assertThat(watcher.markSilentEdges()).isZero();

        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(events.kept).isEmpty();
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void afterAStartThereIsAGraceBeforeAnyoneIsJudged() {
        online(3L, 300L, T0.minus(Duration.ofHours(1)));
        var watcher = watcher(Duration.ofMinutes(2));

        clock.now.set(T0.plusSeconds(119));
        assertThat(watcher.markSilentEdges()).isZero();
        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.ONLINE);

        clock.now.set(T0.plusSeconds(120));
        assertThat(watcher.markSilentEdges()).isEqualTo(1);
    }

    @Test
    void anEdgeThatIsAlreadyOfflineIsNotAnnouncedAgain() {
        edges.kept.put(3L, new EdgeStatus(3L, 33L, 300L, T0, EdgeLink.OFFLINE, 0, T0.plusSeconds(200)));
        clock.now.set(T0.plus(Duration.ofHours(1)));

        assertThat(watcher(Duration.ZERO).markSilentEdges()).isZero();

        assertThat(events.kept).isEmpty();
    }

    @Test
    void anEdgeThatReportedAfterBeingListedIsLeftAlone() {
        online(3L, 300L, T0);
        var listedAsSilent = edges.kept.get(3L);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));
        // The listing is stale: the edge reported between the search and the check.
        edges.staleListing = List.of(listedAsSilent);
        online(3L, 300L, clock.instant().minusSeconds(1));

        assertThat(watcher(Duration.ZERO).markSilentEdges()).isZero();

        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(events.kept).isEmpty();
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ifTheFamilyCannotBeToldTheEdgeStaysOnlineAndTheNextRunTriesAgain() {
        online(3L, 300L, T0);
        var watcher = watcher(Duration.ZERO);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));
        doThrow(new IllegalStateException("notifications are down")).when(notifications)
                .notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());

        assertThat(watcher.markSilentEdges()).isZero();

        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(meters.get("tracking.link.failures").tag("stage", "edge").counter().count()).isEqualTo(1.0);

        // The service is back: it is told and marked, and the event is not written twice.
        org.mockito.Mockito.reset(notifications);
        when(notifications.relativesOf(300L)).thenReturn(Set.of(8L));
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        clock.now.set(T0.plus(AFTER).plusSeconds(31));
        assertThat(watcher.markSilentEdges()).isEqualTo(1);

        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.OFFLINE);
        assertThat(events.ofType(EventType.EDGE_DISCONNECTED)).hasSize(1);
        verify(notifications, times(1)).notifyOnce(eq(8L), eq("EDGE_DISCONNECTED"), anyString(), anyString(), anyString());
    }

    @Test
    void ifTheEventCannotBeWrittenNobodyIsToldAndTheEdgeStaysOnline() {
        online(3L, 300L, T0);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));
        events.failing = true;

        assertThat(watcher(Duration.ZERO).markSilentEdges()).isZero();

        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.ONLINE);
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void oneEdgeFailingDoesNotStopTheOthers() {
        online(3L, 300L, T0);
        online(4L, 301L, T0);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));
        doThrow(new IllegalStateException("boom")).when(notifications)
                .notifyOnce(eq(8L), anyString(), anyString(), anyString(), anyString());

        assertThat(watcher(Duration.ZERO).markSilentEdges()).isEqualTo(1);

        assertThat(edges.kept.get(3L).link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(edges.kept.get(4L).link()).isEqualTo(EdgeLink.OFFLINE);
    }

    @Test
    void twoInstancesRunningTogetherMakeOneTransition() {
        online(3L, 300L, T0);
        clock.now.set(T0.plus(AFTER).plusSeconds(1));
        var first = watcher(Duration.ZERO);
        var second = watcher(Duration.ZERO);
        // Both listed it as silent before either marked it.
        edges.staleListing = List.copyOf(edges.kept.values());

        int total = first.markSilentEdges() + second.markSilentEdges();

        assertThat(total).isEqualTo(1);
        assertThat(edges.marks).isEqualTo(1);
        assertThat(events.ofType(EventType.EDGE_DISCONNECTED)).hasSize(1);
    }

    @Test
    void aHomeWithoutRelativesInChargeIsStillMarkedAndWritten() {
        online(3L, 302L, T0);
        when(notifications.relativesOf(302L)).thenReturn(Set.of());
        clock.now.set(T0.plus(AFTER).plusSeconds(1));

        assertThat(watcher(Duration.ZERO).markSilentEdges()).isEqualTo(1);

        assertThat(events.ofType(EventType.EDGE_DISCONNECTED)).hasSize(1);
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void theScheduledRunNeverThrows() {
        var broken = new InMemoryEdges() {
            @Override
            public List<EdgeStatus> findOnlineSilentSince(Instant limit) {
                throw new IllegalStateException("MongoDB is down");
            }
        };
        var watcher = new EdgeLinkWatcher(broken, events, notifications, clock, meters, AFTER, Duration.ZERO);

        watcher.run();

        assertThat(meters.get("tracking.link.failures").tag("stage", "search").counter().count()).isEqualTo(1.0);
    }

    @Test
    void anInvalidConfigurationIsRefused() {
        assertThatThrownBy(() -> new EdgeLinkWatcher(edges, events, notifications, clock, meters, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EdgeLinkWatcher(edges, events, notifications, clock, meters, AFTER, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
