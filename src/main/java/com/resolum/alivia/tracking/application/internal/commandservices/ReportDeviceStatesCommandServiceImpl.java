package com.resolum.alivia.tracking.application.internal.commandservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.exceptions.BatchTooLarge;
import com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.commands.ReportDeviceStatesCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceStateOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.StateOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.DeviceStateRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.domain.services.ReportDeviceStatesCommandService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Implementation of {@link ReportDeviceStatesCommandService}.
 *
 * <p><b>The order of the writes is what makes a retry safe.</b> The edge is answered only for what is kept, so when
 * something cannot be written the whole report is answered {@code 503} and the edge sends it again. For that to lose
 * nothing, what is derived from the report is written <em>before</em> the state it is derived from: the events of a
 * change (which have an id made from the device and the moment, so repeating them is recognised) and the end of an
 * outage go first, and the state and the heartbeat after. Written the other way round, a retry would find the state
 * already changed and would never produce the event.</p>
 */
@Slf4j
@Service
public class ReportDeviceStatesCommandServiceImpl implements ReportDeviceStatesCommandService {

    private final ExternalAssetsService assets;
    private final DeviceStateRepository states;
    private final EdgeStatusRepository edgeStatuses;
    private final DeviceEventRepository events;
    private final TelemetrySampleRecorder samples;
    private final ExternalNotificationsService notifications;
    private final TrackingPolicy policy;
    private final Clock clock;
    private final MeterRegistry meters;

    public ReportDeviceStatesCommandServiceImpl(ExternalAssetsService assets, DeviceStateRepository states,
                                                EdgeStatusRepository edgeStatuses, DeviceEventRepository events,
                                                TelemetrySampleRecorder samples,
                                                ExternalNotificationsService notifications, TrackingPolicy policy,
                                                Clock clock, MeterRegistry meters) {
        this.assets = assets;
        this.states = states;
        this.edgeStatuses = edgeStatuses;
        this.events = events;
        this.samples = samples;
        this.notifications = notifications;
        this.policy = policy;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public List<DeviceStateOutcome> handle(ReportDeviceStatesCommand command) {
        if (command.devices().size() > policy.maxBatchSize()) {
            throw new BatchTooLarge(command.devices().size(), policy.maxBatchSize());
        }
        var edge = assets.fetchReportingEdge(command.edgeId())
                .orElseThrow(() -> new EdgeNotInInstallation(command.edgeId()));
        var context = edge.context();
        var now = clock.instant();

        var outcomes = new ArrayList<DeviceStateOutcome>(command.devices().size());
        for (var report : command.devices()) {
            var outcome = apply(context, report, now);
            // What was finally decided, not what was first evaluated: a report that loses the race is stale.
            meters.counter("tracking.device.states", "outcome", outcome.status().name().toLowerCase(java.util.Locale.ROOT))
                    .increment();
            outcomes.add(outcome);
        }
        recordHeartbeat(context, now, command.pendingEvents());
        return outcomes;
    }

    private DeviceStateOutcome apply(ReportingContext context, com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState report,
                                     java.time.Instant now) {
        var current = report.deviceId() == null ? null
                : guarded(() -> states.findByDeviceId(report.deviceId()).orElse(null));
        var update = DeviceState.evaluate(current, context, report, now, policy);
        if (update.outcome() != StateOutcome.APPLIED) {
            return new DeviceStateOutcome(report.deviceId(), update.outcome(), update.reason());
        }
        var state = update.state();
        // The events first: they are recognised if the report comes again, and the state is not yet the new one.
        for (DeviceEvent event : state.eventsSince(current, context, now, policy)) {
            guarded(() -> events.insert(event));
        }
        if (!guarded(() -> states.saveUnlessOlder(state))) {
            // Another report observed later got in first: this one is late.
            return new DeviceStateOutcome(report.deviceId(), StateOutcome.STALE, null);
        }
        samples.record(state, update.diagnostics());
        return new DeviceStateOutcome(report.deviceId(), StateOutcome.APPLIED, null);
    }

    private void recordHeartbeat(ReportingContext context, java.time.Instant now, int pending) {
        var before = guarded(() -> edgeStatuses.findByEdgeId(context.edgeId()).orElse(null));
        if (before != null && before.link() == EdgeLink.OFFLINE) {
            // The end of the outage, with an id made from the moment it started: however many reports of the edge arrive
            // at once, there is one reconnection.
            var reconnected = DeviceEvent.byServer(
                    "rc-edge-" + context.edgeId() + "-" + before.offlineSince().toEpochMilli(),
                    EventType.EDGE_RECONNECTED, context.edgeId(), context.installationId(), context.householdId(),
                    null, now, null);
            guarded(() -> events.insert(reconnected));
            tellTheFamilyItIsBack(context, reconnected.eventId());
        }
        var heartbeat = EdgeStatus.heartbeat(before, context, now, pending);
        guarded(() -> edgeStatuses.recordHeartbeat(heartbeat.status()));
    }

    /**
     * Tells the relatives that the edge is back. It is the good news and never holds the report: if it fails, the event is
     * already in the history and the report is answered, which is worth more than the notice.
     */
    private void tellTheFamilyItIsBack(ReportingContext context, String eventId) {
        try {
            for (Long userId : notifications.relativesOf(context.householdId())) {
                notifications.notifyOnce(userId, EdgeLinkMessages.RECONNECTED_TYPE, eventId,
                        EdgeLinkMessages.RECONNECTED_TITLE, EdgeLinkMessages.RECONNECTED_BODY);
            }
        } catch (RuntimeException failure) {
            meters.counter("tracking.link.failures", "stage", "reconnection_notice").increment();
            log.warn("The relatives of the home {} could not be told that the edge {} is back.",
                    context.householdId(), context.edgeId(), failure);
        }
    }

    /** Runs a write; if the database fails the edge is not told that it was kept, and it sends the report again. */
    private <T> T guarded(Supplier<T> write) {
        try {
            return write.get();
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "report_states").increment();
            log.error("The state report of an edge could not be kept.", failure);
            throw new TrackingStorageUnavailable(failure);
        }
    }
}
