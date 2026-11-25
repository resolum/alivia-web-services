package com.resolum.alivia.tracking.application.internal.commandservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.exceptions.BatchTooLarge;
import com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation;
import com.resolum.alivia.tracking.domain.exceptions.InvalidTrackingData;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.commands.ReportEventBatchCommand;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.RecordEdgeEventsCommandService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of {@link RecordEdgeEventsCommandService}.
 *
 * <p>The family is told before the event is kept, with {@code notifyOnce} on the id the edge gave it: a resend after a
 * network loss notifies nobody twice. A single urgent report whose event cannot be kept still answers, since the family
 * has already been told; the failure is logged as an error and counted so it is seen and not silent. A batch is stricter:
 * it is answered only for what is really kept, so an edge never forgets an event that was not saved.</p>
 */
@Slf4j
@Service
public class RecordEdgeEventsCommandServiceImpl implements RecordEdgeEventsCommandService {

    /** The same source key the notifications have always had, so an event already notified is not notified again. */
    private static final String SOURCE_PREFIX = "edge-";

    private final ExternalAssetsService assets;
    private final ExternalNotificationsService notifications;
    private final DeviceEventRepository events;
    private final TrackingPolicy policy;
    private final Clock clock;
    private final MeterRegistry meters;

    public RecordEdgeEventsCommandServiceImpl(ExternalAssetsService assets, ExternalNotificationsService notifications,
                                              DeviceEventRepository events, TrackingPolicy policy, Clock clock,
                                              MeterRegistry meters) {
        this.assets = assets;
        this.notifications = notifications;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public int handle(ReportUrgentEventCommand command) {
        var edge = reportingEdge(command.edgeId());
        UrgentReport urgent;
        try {
            urgent = tellTheFamily(edge, clock.instant(), command, false);
        } catch (InvalidTrackingData invalid) {
            counted("rejected", "single");
            throw invalid;
        }
        try {
            counted(events.insert(urgent.event()) == EventInsertion.INSERTED ? "accepted" : "duplicate", "single");
        } catch (RuntimeException failure) {
            countFailure();
            log.error("The event {} of the edge {} could not be kept; the relatives were told anyway.",
                    urgent.event().eventId(), urgent.event().edgeId(), failure);
        }
        return urgent.notified();
    }

    @Override
    public List<EventOutcome> handle(ReportEventBatchCommand command) {
        if (command.events().size() > policy.maxBatchSize()) {
            throw new BatchTooLarge(command.events().size(), policy.maxBatchSize());
        }
        var edge = reportingEdge(command.edgeId());
        var receivedAt = clock.instant();
        var outcomes = new ArrayList<EventOutcome>(command.events().size());
        for (var reported : command.events()) {
            outcomes.add(record(edge, receivedAt, reported));
        }
        return outcomes;
    }

    private EventOutcome record(ReportingEdge edge, Instant receivedAt, ReportedEvent reported) {
        var eventId = reported.eventId();
        if (eventId == null || !DeviceEvent.EVENT_ID.matcher(eventId).matches()) {
            counted("rejected", "batch");
            return EventOutcome.rejected(eventId, RejectionReason.INVALID_EVENT_ID);
        }
        var type = urgentTypeOf(reported.type());
        try {
            DeviceEvent event;
            if (type != null) {
                if (reported.batteryPct() != null && (reported.batteryPct() < 0 || reported.batteryPct() > 100)) {
                    counted("rejected", "batch");
                    return EventOutcome.rejected(eventId, RejectionReason.BATTERY_OUT_OF_RANGE);
                }
                // Urgent: told to the family and never refused for what it says about a device or the clock.
                event = tellTheFamily(edge, receivedAt, new ReportUrgentEventCommand(edge.context().edgeId(), eventId,
                        type, reported.deviceId(), reported.batteryPct(), reported.detail(), reported.occurredAt()),
                        true).event();
            } else {
                event = DeviceEvent.fromEdge(edge.context(), reported, receivedAt, true, policy);
            }
            if (keep(event) == EventInsertion.INSERTED) {
                counted("accepted", "batch");
                return EventOutcome.accepted(eventId);
            }
            counted("duplicate", "batch");
            return EventOutcome.duplicate(eventId);
        } catch (InvalidTrackingData invalid) {
            counted("rejected", "batch");
            return EventOutcome.rejected(eventId, invalid.reason());
        }
    }

    private EventInsertion keep(DeviceEvent event) {
        try {
            return events.insert(event);
        } catch (RuntimeException failure) {
            countFailure();
            log.error("The event {} of the edge {} could not be kept.", event.eventId(), event.edgeId(), failure);
            throw new TrackingStorageUnavailable(failure);
        }
    }

    /** Builds the event of an urgent report and tells the relatives in charge once per event id; it does not keep it. */
    private UrgentReport tellTheFamily(ReportingEdge edge, Instant receivedAt, ReportUrgentEventCommand command,
                                       boolean viaBatch) {
        var context = edge.context();
        // An urgent report is never lost for being imprecise: a device that is not in the table is left out, a clock
        // that is ahead is replaced by the moment of reception, and a long text is cut.
        var deviceId = context.knows(command.deviceId()) ? command.deviceId() : null;
        var occurredAt = command.occurredAt() == null
                || command.occurredAt().isAfter(receivedAt.plus(policy.clockTolerance()))
                ? receivedAt : command.occurredAt();
        var reported = new ReportedEvent(command.eventId(), command.type().name(), occurredAt, deviceId, null, null,
                null, null, command.batteryPct(), cut(command.detail(), policy.maxDetail()), null);
        var event = DeviceEvent.fromEdge(context, reported, receivedAt, viaBatch, policy);

        // A request for help that arrives long after it was made is told as such. If that very event is already kept, the
        // family was told when it was kept (the notice goes before the write), so it is not told again: the type changes with
        // the delay, and Communications would not recognise the notice as the one it already has. Looking is only done for a
        // late one, which is not urgent, so a database that does not answer never delays the notice of a live request.
        boolean delayed = command.type() == EventType.HELP_REQUESTED && command.occurredAt() != null
                && command.occurredAt().isBefore(receivedAt.minus(policy.helpDelayedAfter()));
        if (delayed && alreadyKept(event)) {
            return new UrgentReport(event, 0);
        }
        var message = delayed ? EdgeEventMessages.helpRequestDelayed(event, policy.zone())
                : EdgeEventMessages.of(event, deviceId == null ? null : edge.deviceAliases().get(deviceId));
        int notified = 0;
        for (Long userId : notifications.relativesOf(context.householdId())) {
            if (notifications.notifyOnce(userId, message.type(), SOURCE_PREFIX + event.eventId(), message.title(),
                    message.body())) {
                notified++;
            }
        }
        return new UrgentReport(event, notified);
    }

    private boolean alreadyKept(DeviceEvent event) {
        try {
            return events.findByEdgeAndEventId(event.edgeId(), event.eventId()).isPresent();
        } catch (RuntimeException failure) {
            // Not knowing is not a reason to stay silent about a request for help.
            meters.counter("tracking.storage.failures", "operation", "check_event").increment();
            log.warn("It could not be checked whether the event {} of the edge {} is already kept.", event.eventId(),
                    event.edgeId(), failure);
            return false;
        }
    }

    private ReportingEdge reportingEdge(Long edgeId) {
        return assets.fetchReportingEdge(edgeId).orElseThrow(() -> new EdgeNotInInstallation(edgeId));
    }

    /** The urgent type the text names, or {@code null} when it is any other. */
    private static EventType urgentTypeOf(String text) {
        if (text == null) return null;
        try {
            var type = EventType.valueOf(text.strip().toUpperCase(Locale.ROOT));
            return type.isUrgent() ? type : null;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** What became of an event the edge reported: {@code accepted}, {@code duplicate} or {@code rejected}, and how it came. */
    private void counted(String outcome, String via) {
        meters.counter("tracking.events", "outcome", outcome, "via", via).increment();
    }

    private void countFailure() {
        meters.counter("tracking.storage.failures", "operation", "insert_event").increment();
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private record UrgentReport(DeviceEvent event, int notified) {
    }
}
