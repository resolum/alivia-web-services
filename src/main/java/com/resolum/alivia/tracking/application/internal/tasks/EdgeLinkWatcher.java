package com.resolum.alivia.tracking.application.internal.tasks;

import com.resolum.alivia.tracking.application.internal.commandservices.EdgeLinkMessages;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Notices the edges that stopped reporting, writes it in the history and tells the relatives in charge of the home.
 *
 * <p>The state it reads is the one kept in MongoDB ({@code lastReportAt}), never one in memory, so a restart of the
 * backend does not forget what it knew. Right after starting it waits {@code startup-grace} before judging anyone: the
 * edges could not report while the backend was down and need time to do it again.</p>
 *
 * <p><b>The order is what makes a failure recoverable.</b> The event and the notification go first, both recognised if
 * repeated (the event has an id made from the last report of the edge, the notification is sent once per source), and the
 * edge is marked offline last, atomically, by whoever gets there first. If the notification service fails the edge stays
 * online and the next run tries again; marking it first would leave it offline with nobody told, and nothing would look at
 * it again. The price is a rare false alarm: if the edge reports between the check and the mark, the relatives are told
 * about a disconnection that did not hold.</p>
 */
@Slf4j
@Component
public class EdgeLinkWatcher {

    private final EdgeStatusRepository edges;
    private final DeviceEventRepository events;
    private final ExternalAssetsService assets;
    private final ExternalNotificationsService notifications;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Duration offlineAfter;
    private final Instant graceEndsAt;

    public EdgeLinkWatcher(EdgeStatusRepository edges, DeviceEventRepository events, ExternalAssetsService assets,
                           ExternalNotificationsService notifications, Clock clock, MeterRegistry meters,
                           @Value("${alivia.tracking.edge-offline-after:PT3M}") Duration offlineAfter,
                           @Value("${alivia.tracking.link-startup-grace:PT2M}") Duration startupGrace) {
        if (offlineAfter == null || offlineAfter.isNegative() || offlineAfter.isZero()) {
            throw new IllegalArgumentException("The silence after which an edge is offline must be positive.");
        }
        if (startupGrace == null || startupGrace.isNegative()) {
            throw new IllegalArgumentException("The startup grace cannot be negative.");
        }
        this.edges = edges;
        this.events = events;
        this.assets = assets;
        this.notifications = notifications;
        this.clock = clock;
        this.meters = meters;
        this.offlineAfter = offlineAfter;
        this.graceEndsAt = clock.instant().plus(startupGrace);
    }

    @Scheduled(fixedDelayString = "${alivia.tracking.link-check-interval:PT30S}",
            initialDelayString = "${alivia.tracking.link-check-interval:PT30S}")
    public void run() {
        try {
            markSilentEdges();
        } catch (RuntimeException failure) {
            failed("search");
            log.error("The check of the edge links failed.", failure);
        }
    }

    /**
     * Marks as offline the edges that have not reported for longer than allowed.
     *
     * @return how many edges this run marked; an edge that another instance marked first is not counted
     */
    public int markSilentEdges() {
        var now = clock.instant();
        if (now.isBefore(graceEndsAt)) return 0;
        int marked = 0;
        for (EdgeStatus silent : edges.findOnlineSilentSince(now.minus(offlineAfter))) {
            try {
                if (disconnect(silent.edgeId(), now)) marked++;
            } catch (RuntimeException failure) {
                // One edge failing does not stop the others, and it is tried again in the next run.
                failed("edge");
                log.error("The edge {} could not be marked as disconnected.", silent.edgeId(), failure);
            }
        }
        return marked;
    }

    private boolean disconnect(Long edgeId, Instant now) {
        // Read again: it may have reported since it was listed, or another instance may have marked it.
        var current = edges.findByEdgeId(edgeId).orElse(null);
        if (current == null || current.markedOffline(now, offlineAfter).isEmpty()) return false;

        // An edge that was replaced, or taken out of the installation, falls silent for good: it is not a loss of connection
        // of the home and nobody is told. Its status is forgotten, so it is not looked at again and, if the edge is placed in
        // another home, its first report there is a first report.
        var assigned = assets.fetchReportingEdge(edgeId);
        if (assigned.isEmpty() || !java.util.Objects.equals(assigned.get().context().householdId(), current.householdId())) {
            if (edges.removeIfSilent(edgeId, current.lastReportAt())) {
                meters.counter("tracking.link.retired").increment();
                log.info("The edge {} is no longer assigned to the household {}: its status was forgotten.", edgeId,
                        current.householdId());
            }
            return false;
        }

        var lastReport = current.lastReportAt();
        var eventId = "dc-edge-" + edgeId + "-" + lastReport.toEpochMilli();
        events.insert(DeviceEvent.byServer(eventId, EventType.EDGE_DISCONNECTED, edgeId, current.installationId(),
                current.householdId(), null, now, "Sin reportes desde " + lastReport));
        for (Long userId : notifications.relativesOf(current.householdId())) {
            notifications.notifyOnce(userId, EdgeLinkMessages.DISCONNECTED_TYPE, eventId,
                    EdgeLinkMessages.DISCONNECTED_TITLE, EdgeLinkMessages.disconnectedBody(current.kind()));
        }
        boolean won = edges.markOffline(edgeId, lastReport, now);
        if (won) meters.counter("tracking.link.transitions", "to", "offline").increment();
        return won;
    }

    private void failed(String stage) {
        meters.counter("tracking.link.failures", "stage", stage).increment();
    }
}
