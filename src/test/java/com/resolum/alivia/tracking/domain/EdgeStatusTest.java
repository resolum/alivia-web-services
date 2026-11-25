package com.resolum.alivia.tracking.domain;

import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Whether the backend hears from the edge, measured with the clock of the backend. */
class EdgeStatusTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:00:00Z");
    private static final Duration LIMIT = Duration.ofMinutes(3);
    private static final ReportingContext CONTEXT = new ReportingContext(3L, 30L, 300L, Set.of(10L));

    @Test
    void theFirstReportMakesTheEdgeOnlineWithoutCountingAsAReconnection() {
        var heartbeat = EdgeStatus.heartbeat(null, CONTEXT, T0, 3);

        assertThat(heartbeat.reconnected()).isFalse();
        var status = heartbeat.status();
        assertThat(status.link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(status.lastReportAt()).isEqualTo(T0);
        assertThat(status.pendingEvents()).isEqualTo(3);
        assertThat(status.offlineSince()).isNull();
        assertThat(status.mayHavePendingEvents()).isTrue();
        assertThat(EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status().mayHavePendingEvents()).isFalse();
    }

    @Test
    void anEdgeIsOfflineOnlyAfterTheAllowedSilence() {
        var status = EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status();

        assertThat(status.markedOffline(T0.plus(LIMIT), LIMIT)).isEmpty();
        var offline = status.markedOffline(T0.plus(LIMIT).plusSeconds(1), LIMIT);
        assertThat(offline).isPresent();
        assertThat(offline.get().link()).isEqualTo(EdgeLink.OFFLINE);
        assertThat(offline.get().offlineSince()).isEqualTo(T0.plus(LIMIT).plusSeconds(1));
        // The last report stays the last time it was heard, not the time of the verdict.
        assertThat(offline.get().lastReportAt()).isEqualTo(T0);
        assertThat(offline.get().mayHavePendingEvents()).isTrue();
    }

    @Test
    void anOfflineEdgeIsNotMarkedTwiceSoTheEventAndTheNotificationHappenOnce() {
        var offline = EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status()
                .markedOffline(T0.plus(Duration.ofMinutes(10)), LIMIT).orElseThrow();

        assertThat(offline.markedOffline(T0.plus(Duration.ofMinutes(20)), LIMIT)).isEmpty();
    }

    @Test
    void theNextReportEndsTheOutageAndIsTheOneReconnection() {
        var offline = EdgeStatus.heartbeat(null, CONTEXT, T0, 0).status()
                .markedOffline(T0.plus(Duration.ofMinutes(10)), LIMIT).orElseThrow();

        var back = EdgeStatus.heartbeat(offline, CONTEXT, T0.plus(Duration.ofMinutes(11)), 12);
        assertThat(back.reconnected()).isTrue();
        assertThat(back.status().link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(back.status().offlineSince()).isNull();
        assertThat(back.status().pendingEvents()).isEqualTo(12);

        var next = EdgeStatus.heartbeat(back.status(), CONTEXT, T0.plus(Duration.ofMinutes(12)), 0);
        assertThat(next.reconnected()).isFalse();
    }

    @Test
    void aNegativeCountOfPendingEventsIsReadAsNoneAndTheContextIsRequired() {
        assertThat(EdgeStatus.heartbeat(null, CONTEXT, T0, -4).status().pendingEvents()).isZero();
        assertThatThrownBy(() -> new EdgeStatus(3L, 30L, 300L, T0, EdgeLink.ONLINE, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportingContext(null, 30L, 300L, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theKindOfTheEdgeIsKeptByTheHeartbeatAndByTheTransitionToOffline() {
        var first = EdgeStatus.heartbeat(null, CONTEXT, T0, 0, "MICROPHONE").status();
        var next = EdgeStatus.heartbeat(first, CONTEXT, T0.plusSeconds(30), 0).status();
        var silent = next.markedOffline(T0.plus(LIMIT).plusSeconds(31), LIMIT).orElseThrow();

        assertThat(first.kind()).isEqualTo("MICROPHONE");
        // A heartbeat that does not say the kind keeps the one already known.
        assertThat(next.kind()).isEqualTo("MICROPHONE");
        assertThat(silent.kind()).isEqualTo("MICROPHONE");
        assertThat(EdgeStatus.heartbeat(silent, CONTEXT, T0.plusSeconds(400), 0, "ACTUATOR").status().kind())
                .isEqualTo("ACTUATOR");
    }

    @Test
    void aStatusKeptBeforeTheKindWasRecordedHasNone() {
        var old = new EdgeStatus(3L, 30L, 300L, T0, EdgeLink.ONLINE, 0, null);

        assertThat(old.kind()).isNull();
        assertThat(EdgeStatus.heartbeat(old, CONTEXT, T0.plusSeconds(30), 0).status().kind()).isNull();
    }
}
