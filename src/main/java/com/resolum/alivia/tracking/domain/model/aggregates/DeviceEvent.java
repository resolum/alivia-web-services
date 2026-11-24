package com.resolum.alivia.tracking.domain.model.aggregates;

import com.resolum.alivia.tracking.domain.exceptions.InvalidTrackingData;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandAction;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Something that happened in a home: a command and how it ended, a change of state, a failure, a low battery, a request
 * for help or the loss of the connection. A fact: once it is kept it is never changed.
 *
 * <p>An edge identifies each event with an {@code eventId} that it keeps when it resends the event, so the pair
 * (edge, {@code eventId}) is its identity and a repeated report is recognised. The server decides the severity and the
 * origin from the report, never the edge.</p>
 *
 * @param eventId        identifier the edge gives the event (the server gives its own to the ones it produces)
 * @param edgeId         the edge that reported it
 * @param installationId the installation of the edge when it was reported
 * @param householdId    the household of the installation when it was reported: the history stays with that home even if
 *                       a device is later placed in another
 * @param deviceId       the device involved, when there is one
 * @param type           what happened
 * @param severity       how much it matters
 * @param result         how a command ended or why it was not run; {@code null} for the other types
 * @param origin         whether it arrived when it happened or late
 * @param occurredAt     when it happened, by the clock of the edge
 * @param receivedAt     when the backend received it, by its own clock
 * @param commandId      the id that joins the request, the result and the rejection of one command
 * @param action         the verb of the command
 * @param confidence     the confidence of the voice model, 0 to 1
 * @param batteryPct     the battery left, 0 to 100
 * @param detail         a short description
 * @param attributes     what varies from one device to another
 */
public record DeviceEvent(String eventId, Long edgeId, Long installationId, Long householdId, Long deviceId,
                          EventType type, EventSeverity severity, EventResult result, EventOrigin origin,
                          Instant occurredAt, Instant receivedAt, String commandId, CommandAction action,
                          Double confidence, Integer batteryPct, String detail, EventAttributes attributes) {

    /** The pattern of an {@code eventId}: the same one the edge events endpoint has always accepted. */
    public static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9._:-]{1,48}");
    private static final Pattern COMMAND_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    /**
     * Validates what an edge reported and builds the event.
     *
     * @param viaBatch whether it came in a batch of pending events, which is what makes a late arrival «executed locally»
     * @throws InvalidTrackingData with the reason the edge reads when the report is not valid
     */
    public static DeviceEvent fromEdge(ReportingContext context, ReportedEvent reported, Instant receivedAt,
                                       boolean viaBatch, TrackingPolicy policy) {
        var eventId = reported.eventId();
        if (eventId == null || !EVENT_ID.matcher(eventId).matches()) {
            throw invalid(RejectionReason.INVALID_EVENT_ID, "The event id must have 1 to 48 letters, digits or . _ : -");
        }
        var type = parseType(reported.type());
        var occurredAt = reported.occurredAt();
        if (occurredAt == null) {
            throw invalid(RejectionReason.INVALID_TIMESTAMP, "When the event happened is required.");
        }
        if (occurredAt.isAfter(receivedAt.plus(policy.clockTolerance()))) {
            throw invalid(RejectionReason.OCCURRED_IN_FUTURE, "The event is dated in the future.");
        }
        var deviceId = reported.deviceId();
        if (deviceId == null && type.requiresDevice()) {
            throw invalid(RejectionReason.DEVICE_REQUIRED, "The device is required for " + type + ".");
        }
        if (deviceId != null && !context.knows(deviceId)) {
            throw invalid(RejectionReason.UNKNOWN_DEVICE, "The device is not in the table of the installation.");
        }
        var commandId = commandIdOf(type, reported.commandId());
        var action = actionOf(type, reported.action());
        var result = resultOf(type, reported.result());
        var confidence = reported.confidence();
        if (confidence != null && (confidence.isNaN() || confidence < 0 || confidence > 1)) {
            throw invalid(RejectionReason.CONFIDENCE_OUT_OF_RANGE, "The confidence must be between 0 and 1.");
        }
        var batteryPct = reported.batteryPct();
        if (batteryPct != null && (batteryPct < 0 || batteryPct > 100)) {
            throw invalid(RejectionReason.BATTERY_OUT_OF_RANGE, "The battery must be between 0 and 100.");
        }
        var detail = detailOf(reported.detail(), policy);
        var attributes = EventAttributes.of(reported.attributes(), policy);
        var origin = viaBatch && occurredAt.isBefore(receivedAt.minus(policy.syncedAfter()))
                ? EventOrigin.SYNCED : EventOrigin.LIVE;
        return new DeviceEvent(eventId, context.edgeId(), context.installationId(), context.householdId(), deviceId,
                type, type.severity(result), result, origin, occurredAt, receivedAt, commandId, action, confidence,
                batteryPct, detail, attributes);
    }

    /**
     * An event that only the server can produce: the loss or the return of the connection of an edge or of a device.
     *
     * @param eventId the id the server gives it; it must be the same for the same fact, so producing it twice is
     *                recognised
     * @throws IllegalArgumentException when the type is one that edges report
     */
    public static DeviceEvent byServer(String eventId, EventType type, Long edgeId, Long installationId,
                                       Long householdId, Long deviceId, Instant occurredAt, String detail) {
        if (type.reportedByEdge()) {
            throw new IllegalArgumentException(type + " is reported by the edges, not produced by the server.");
        }
        if (eventId == null || !EVENT_ID.matcher(eventId).matches()) {
            throw new IllegalArgumentException("The event id must have 1 to 48 letters, digits or . _ : -");
        }
        if (type.requiresDevice() && deviceId == null) {
            throw new IllegalArgumentException("The device is required for " + type + ".");
        }
        return new DeviceEvent(eventId, edgeId, installationId, householdId, deviceId, type, type.severity(null), null,
                EventOrigin.LIVE, occurredAt, occurredAt, null, null, null, null, detail, EventAttributes.empty());
    }

    private static EventType parseType(String text) {
        try {
            var type = text == null ? null : EventType.valueOf(text.strip().toUpperCase(Locale.ROOT));
            if (type != null && type.reportedByEdge()) return type;
        } catch (IllegalArgumentException unknown) {
            // falls through to the rejection
        }
        throw invalid(RejectionReason.TYPE_NOT_ALLOWED, "An edge may not report the type " + text + ".");
    }

    private static String commandIdOf(EventType type, String commandId) {
        if (!type.isCommandRelated()) return null;
        if (commandId == null || !COMMAND_ID.matcher(commandId).matches()) {
            throw invalid(RejectionReason.COMMAND_DATA_REQUIRED, "A valid command id is required for " + type + ".");
        }
        return commandId;
    }

    private static CommandAction actionOf(EventType type, String text) {
        if (text == null || text.isBlank()) {
            if (type.requiresAction()) {
                throw invalid(RejectionReason.COMMAND_DATA_REQUIRED, "The action is required for " + type + ".");
            }
            return null;
        }
        Optional<CommandAction> action = CommandAction.parse(text);
        return action.orElseThrow(() -> invalid(RejectionReason.INVALID_ACTION, "The action must be open, close, on or off."));
    }

    private static EventResult resultOf(EventType type, String text) {
        if (!type.hasResult()) {
            if (text != null && !text.isBlank()) {
                throw invalid(RejectionReason.INVALID_RESULT, type + " carries no result.");
            }
            return null;
        }
        try {
            var result = text == null ? null : EventResult.valueOf(text.strip().toUpperCase(Locale.ROOT));
            if (result != null && type.allowedResults().contains(result)) return result;
        } catch (IllegalArgumentException unknown) {
            // falls through to the rejection
        }
        throw invalid(RejectionReason.INVALID_RESULT, "The result is not valid for " + type + ".");
    }

    private static String detailOf(String detail, TrackingPolicy policy) {
        if (detail == null || detail.isBlank()) return null;
        var stripped = detail.strip();
        if (stripped.length() > policy.maxDetail()) {
            throw invalid(RejectionReason.DETAIL_TOO_LONG, "The detail has more than " + policy.maxDetail() + " characters.");
        }
        return stripped;
    }

    private static InvalidTrackingData invalid(RejectionReason reason, String message) {
        return new InvalidTrackingData(reason, message);
    }
}
