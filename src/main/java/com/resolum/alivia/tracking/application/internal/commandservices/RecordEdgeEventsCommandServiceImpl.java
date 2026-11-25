package com.resolum.alivia.tracking.application.internal.commandservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.RecordEdgeEventsCommandService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Implementation of {@link RecordEdgeEventsCommandService}.
 *
 * <p>The family is told before the event is kept, with {@code notifyOnce} on the id the edge gave it: a resend after a
 * network loss notifies nobody twice. If keeping the event fails (MongoDB down) the family has already been told and the
 * edge gets its answer; the failure is logged as an error and counted, so it is seen and not silent.</p>
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
        var edge = assets.fetchReportingEdge(command.edgeId())
                .orElseThrow(() -> new EdgeNotInInstallation(command.edgeId()));
        var context = edge.context();
        var receivedAt = clock.instant();
        // An urgent report is never lost for being imprecise: a device that is not in the table is left out, a clock
        // that is ahead is replaced by the moment of reception, and a long text is cut.
        var deviceId = context.knows(command.deviceId()) ? command.deviceId() : null;
        var occurredAt = command.occurredAt() == null
                || command.occurredAt().isAfter(receivedAt.plus(policy.clockTolerance()))
                ? receivedAt : command.occurredAt();
        var reported = new ReportedEvent(command.eventId(), command.type().name(), occurredAt, deviceId, null, null,
                null, null, command.batteryPct(), cut(command.detail(), policy.maxDetail()), null);
        var event = DeviceEvent.fromEdge(context, reported, receivedAt, false, policy);

        var message = EdgeEventMessages.of(event, deviceId == null ? null : edge.deviceAliases().get(deviceId));
        int notified = 0;
        for (Long userId : notifications.relativesOf(context.householdId())) {
            if (notifications.notifyOnce(userId, message.type(), SOURCE_PREFIX + event.eventId(), message.title(),
                    message.body())) {
                notified++;
            }
        }
        keep(event);
        return notified;
    }

    private void keep(DeviceEvent event) {
        try {
            events.insert(event);
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "insert_event").increment();
            log.error("The event {} of the edge {} could not be kept; the relatives were told anyway.",
                    event.eventId(), event.edgeId(), failure);
        }
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
