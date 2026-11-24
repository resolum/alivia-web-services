package com.resolum.alivia.tracking.domain.model.aggregates;

import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Whether the backend hears from the edge of a home. Every correct report of the edge is a heartbeat; an edge that stays
 * silent for longer than the allowed time is {@code OFFLINE} until it reports again.
 *
 * <p>The silence is measured with the clock of the backend, never with the one of the edge: what matters is when the
 * backend last heard from it.</p>
 *
 * @param edgeId         the edge
 * @param installationId its installation
 * @param householdId    the household of the installation
 * @param lastReportAt   when the backend last heard from it
 * @param link           whether it is heard
 * @param pendingEvents  how many events the edge said it still has to send
 * @param offlineSince   since when it is not heard ({@code null} while online)
 */
public record EdgeStatus(Long edgeId, Long installationId, Long householdId, Instant lastReportAt, EdgeLink link,
                         int pendingEvents, Instant offlineSince) {

    public EdgeStatus {
        if (pendingEvents < 0) {
            throw new IllegalArgumentException("The pending events cannot be negative.");
        }
    }

    /** What a heartbeat did: the status that follows and whether it ended an outage. */
    public record Heartbeat(EdgeStatus status, boolean reconnected) {
    }

    /**
     * Records a heartbeat.
     *
     * @param current the status kept, or {@code null} the first time the edge reports
     * @param pending how many events the edge still has to send; a negative number is read as none
     */
    public static Heartbeat heartbeat(EdgeStatus current, ReportingContext context, Instant now, int pending) {
        var status = new EdgeStatus(context.edgeId(), context.installationId(), context.householdId(), now,
                EdgeLink.ONLINE, Math.max(pending, 0), null);
        return new Heartbeat(status, current != null && current.link == EdgeLink.OFFLINE);
    }

    /**
     * Marks the edge as offline when it has been silent for longer than allowed.
     *
     * @return the new status, or empty when it is already offline or still within the allowed silence
     */
    public Optional<EdgeStatus> markedOffline(Instant now, Duration offlineAfter) {
        if (link == EdgeLink.OFFLINE || !lastReportAt.plus(offlineAfter).isBefore(now)) {
            return Optional.empty();
        }
        return Optional.of(new EdgeStatus(edgeId, installationId, householdId, lastReportAt, EdgeLink.OFFLINE,
                pendingEvents, now));
    }

    /** Whether events may be waiting in the edge: it is offline, or it said it still has some to send. */
    public boolean mayHavePendingEvents() {
        return link == EdgeLink.OFFLINE || pendingEvents > 0;
    }
}
