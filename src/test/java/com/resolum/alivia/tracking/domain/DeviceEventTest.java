package com.resolum.alivia.tracking.domain;

import com.resolum.alivia.tracking.domain.exceptions.InvalidTrackingData;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandAction;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What an edge may report, how it is judged and what the server decides about it. */
class DeviceEventTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();
    private static final ReportingContext CONTEXT = new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L, 13L));

    private static ReportedEvent event(String type, Long deviceId, String commandId, String action, String result) {
        return new ReportedEvent("9f1c0e7a2b6d4c3e8a5b1d2f3c4e5a01", type, NOW.minusSeconds(2), deviceId, commandId,
                action, result, null, null, null, null);
    }

    private static DeviceEvent accept(ReportedEvent reported) {
        return DeviceEvent.fromEdge(CONTEXT, reported, NOW, false, POLICY);
    }

    private static RejectionReason rejectionOf(ReportedEvent reported) {
        return rejectionOf(reported, false);
    }

    private static RejectionReason rejectionOf(ReportedEvent reported, boolean viaBatch) {
        try {
            DeviceEvent.fromEdge(CONTEXT, reported, NOW, viaBatch, POLICY);
        } catch (InvalidTrackingData invalid) {
            return invalid.reason();
        }
        return null;
    }

    @Test
    void aCommandResultKeepsWhatTheEdgeReportedAndTheHomeOfTheEdge() {
        var reported = new ReportedEvent("9f1c0e7a2b6d4c3e8a5b1d2f3c4e5a02", "COMMAND_RESULT", NOW.minusSeconds(1), 10L,
                "0b1a2c3d4e5f60718293a4b5c6d7e8f9", "close", "SUCCESS", null, null, null,
                Map.of("state", "closed", "elapsedMs", 700));

        var stored = accept(reported);

        assertThat(stored.edgeId()).isEqualTo(3L);
        assertThat(stored.installationId()).isEqualTo(30L);
        assertThat(stored.householdId()).isEqualTo(300L);
        assertThat(stored.type()).isEqualTo(EventType.COMMAND_RESULT);
        assertThat(stored.result()).isEqualTo(EventResult.SUCCESS);
        assertThat(stored.action()).isEqualTo(CommandAction.CLOSE);
        assertThat(stored.commandId()).isEqualTo("0b1a2c3d4e5f60718293a4b5c6d7e8f9");
        assertThat(stored.attributes().values()).containsEntry("state", "closed").containsEntry("elapsedMs", 700);
        assertThat(stored.receivedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource({
            "COMMAND_REQUESTED,,INFORMATIVE",
            "COMMAND_RESULT,SUCCESS,INFORMATIVE",
            "COMMAND_RESULT,FAILED,IMPORTANT",
            "COMMAND_RESULT,FAILED_TIMEOUT,IMPORTANT",
            "COMMAND_RESULT,NO_CONFIRMATION,IMPORTANT",
            "COMMAND_REJECTED,REJECTED_LOW_CONFIDENCE,INFORMATIVE",
            "COMMAND_REJECTED,REJECTED_UNAUTHORIZED,IMPORTANT",
            "DEVICE_STATE_CHANGED,,INFORMATIVE",
            "DEVICE_RECOVERED,,INFORMATIVE",
            "LOW_BATTERY,,PREVENTIVE",
            "DEVICE_FAILURE,,IMPORTANT",
            "HELP_REQUESTED,,CRITICAL"
    })
    void theServerDecidesTheSeverityFromTheTypeAndTheResult(String type, String result, EventSeverity expected) {
        var reported = event(type, 10L, "cmd-1", "open", result);

        assertThat(accept(reported).severity()).isEqualTo(expected);
    }

    @Test
    void everySeverityOfTheClassificationIsInUse() {
        var used = java.util.EnumSet.noneOf(EventSeverity.class);
        for (var type : EventType.values()) {
            used.add(type.severity(type.hasResult() ? type.allowedResults().iterator().next() : null));
        }
        assertThat(used).containsExactlyInAnyOrder(EventSeverity.values());
    }

    @Test
    void theEventIdMustBeStableAndWellFormed() {
        for (var bad : new String[]{null, "", " ", "has space", "a".repeat(49), "ñandú"}) {
            var reported = new ReportedEvent(bad, "HELP_REQUESTED", NOW, null, null, null, null, null, null, null, null);
            assertThat(rejectionOf(reported)).as(String.valueOf(bad)).isEqualTo(RejectionReason.INVALID_EVENT_ID);
        }
        var ok = new ReportedEvent("a".repeat(48), "HELP_REQUESTED", NOW, null, null, null, null, null, null, null, null);
        assertThat(rejectionOf(ok)).isNull();
    }

    @Test
    void anEdgeCannotReportWhatOnlyTheServerProduces() {
        for (var type : new String[]{"EDGE_DISCONNECTED", "EDGE_RECONNECTED", "DEVICE_DISCONNECTED", "NOT_A_TYPE", null}) {
            assertThat(rejectionOf(event(type, 10L, null, null, null))).as(String.valueOf(type))
                    .isEqualTo(RejectionReason.TYPE_NOT_ALLOWED);
        }
    }

    @Test
    void aDeviceOfAnotherInstallationIsRejectedAndOneOfTheTableIsKept() {
        assertThat(rejectionOf(event("COMMAND_RESULT", 99L, "c", "open", "SUCCESS")))
                .isEqualTo(RejectionReason.UNKNOWN_DEVICE);
        assertThat(rejectionOf(event("LOW_BATTERY", 99L, null, null, null))).isEqualTo(RejectionReason.UNKNOWN_DEVICE);
        assertThat(rejectionOf(event("LOW_BATTERY", 13L, null, null, null))).isNull();
        // A help request may not name a device (the person said it aloud).
        assertThat(rejectionOf(event("HELP_REQUESTED", null, null, null, null))).isNull();
    }

    @Test
    void theTypesThatAreAboutADeviceRequireIt() {
        assertThat(rejectionOf(event("COMMAND_RESULT", null, "c", "open", "SUCCESS")))
                .isEqualTo(RejectionReason.DEVICE_REQUIRED);
        assertThat(rejectionOf(event("DEVICE_STATE_CHANGED", null, null, null, null)))
                .isEqualTo(RejectionReason.DEVICE_REQUIRED);
        // A rejection by low confidence may not know the device.
        assertThat(rejectionOf(event("COMMAND_REJECTED", null, "c", null, "REJECTED_LOW_CONFIDENCE"))).isNull();
    }

    @Test
    void aCommandNeedsItsIdItsActionAndAResultOfItsType() {
        assertThat(rejectionOf(event("COMMAND_RESULT", 10L, null, "open", "SUCCESS")))
                .isEqualTo(RejectionReason.COMMAND_DATA_REQUIRED);
        assertThat(rejectionOf(event("COMMAND_RESULT", 10L, "c", null, "SUCCESS")))
                .isEqualTo(RejectionReason.COMMAND_DATA_REQUIRED);
        assertThat(rejectionOf(event("COMMAND_RESULT", 10L, "c", "explode", "SUCCESS")))
                .isEqualTo(RejectionReason.INVALID_ACTION);
        assertThat(rejectionOf(event("COMMAND_RESULT", 10L, "c", "open", null))).isEqualTo(RejectionReason.INVALID_RESULT);
        assertThat(rejectionOf(event("COMMAND_RESULT", 10L, "c", "open", "REJECTED_LOW_CONFIDENCE")))
                .isEqualTo(RejectionReason.INVALID_RESULT);
        assertThat(rejectionOf(event("COMMAND_REJECTED", null, "c", null, "SUCCESS"))).isEqualTo(RejectionReason.INVALID_RESULT);
        // A type without results does not take one.
        assertThat(rejectionOf(event("HELP_REQUESTED", null, null, null, "SUCCESS"))).isEqualTo(RejectionReason.INVALID_RESULT);
        assertThat(rejectionOf(event("COMMAND_RESULT", 10L, "c", "OPEN", "success"))).isNull();
    }

    @Test
    void theClockOfTheEdgeMayBeALittleAheadButNotMuch() {
        var base = event("HELP_REQUESTED", null, null, null, null);
        assertThat(rejectionOf(withOccurredAt(base, NOW.plus(Duration.ofMinutes(4))))).isNull();
        assertThat(rejectionOf(withOccurredAt(base, NOW.plus(Duration.ofMinutes(6)))))
                .isEqualTo(RejectionReason.OCCURRED_IN_FUTURE);
        assertThat(rejectionOf(withOccurredAt(base, null))).isEqualTo(RejectionReason.INVALID_TIMESTAMP);
    }

    private static ReportedEvent withOccurredAt(ReportedEvent e, Instant at) {
        return new ReportedEvent(e.eventId(), e.type(), at, e.deviceId(), e.commandId(), e.action(), e.result(),
                e.confidence(), e.batteryPct(), e.detail(), e.attributes());
    }

    @Test
    void numbersAndTextsHaveLimits() {
        var base = event("LOW_BATTERY", 13L, null, null, null);
        assertThat(rejectionOf(withBattery(base, -1))).isEqualTo(RejectionReason.BATTERY_OUT_OF_RANGE);
        assertThat(rejectionOf(withBattery(base, 101))).isEqualTo(RejectionReason.BATTERY_OUT_OF_RANGE);
        assertThat(rejectionOf(withBattery(base, 0))).isNull();
        assertThat(rejectionOf(withBattery(base, 100))).isNull();

        assertThat(rejectionOf(withConfidence(base, 1.01))).isEqualTo(RejectionReason.CONFIDENCE_OUT_OF_RANGE);
        assertThat(rejectionOf(withConfidence(base, Double.NaN))).isEqualTo(RejectionReason.CONFIDENCE_OUT_OF_RANGE);
        assertThat(rejectionOf(withConfidence(base, 0.0))).isNull();

        assertThat(rejectionOf(withDetail(base, "x".repeat(201)))).isEqualTo(RejectionReason.DETAIL_TOO_LONG);
        assertThat(accept(withDetail(base, "  " + "x".repeat(200) + "  ")).detail()).hasSize(200);
        assertThat(accept(withDetail(base, "   ")).detail()).isNull();
    }

    private static ReportedEvent withBattery(ReportedEvent e, Integer pct) {
        return new ReportedEvent(e.eventId(), e.type(), e.occurredAt(), e.deviceId(), e.commandId(), e.action(),
                e.result(), e.confidence(), pct, e.detail(), e.attributes());
    }

    private static ReportedEvent withConfidence(ReportedEvent e, Double confidence) {
        return new ReportedEvent(e.eventId(), e.type(), e.occurredAt(), e.deviceId(), e.commandId(), e.action(),
                e.result(), confidence, e.batteryPct(), e.detail(), e.attributes());
    }

    private static ReportedEvent withDetail(ReportedEvent e, String detail) {
        return new ReportedEvent(e.eventId(), e.type(), e.occurredAt(), e.deviceId(), e.commandId(), e.action(),
                e.result(), e.confidence(), e.batteryPct(), detail, e.attributes());
    }

    @Test
    void anEventThatArrivesLateInABatchIsMarkedAsExecutedLocally() {
        var late = withOccurredAt(event("DEVICE_STATE_CHANGED", 10L, null, null, null), NOW.minusSeconds(61));
        var recent = withOccurredAt(event("DEVICE_STATE_CHANGED", 10L, null, null, null), NOW.minusSeconds(59));

        assertThat(DeviceEvent.fromEdge(CONTEXT, late, NOW, true, POLICY).origin()).isEqualTo(EventOrigin.SYNCED);
        assertThat(DeviceEvent.fromEdge(CONTEXT, recent, NOW, true, POLICY).origin()).isEqualTo(EventOrigin.LIVE);
        // Outside a batch the delay says nothing: it is a single report.
        assertThat(DeviceEvent.fromEdge(CONTEXT, late, NOW, false, POLICY).origin()).isEqualTo(EventOrigin.LIVE);
    }

    @Test
    void anAttributeThatCouldCarryAudioIsRefused() {
        var base = event("COMMAND_RESULT", 10L, "c", "open", "SUCCESS");
        for (var key : new String[]{"audio", "PCM", "Transcript", "wav", "samples"}) {
            var reported = new ReportedEvent(base.eventId(), base.type(), base.occurredAt(), base.deviceId(),
                    base.commandId(), base.action(), base.result(), null, null, null, Map.of(key, "x"));
            assertThat(rejectionOf(reported)).as(key).isEqualTo(RejectionReason.FORBIDDEN_ATTRIBUTE);
        }
    }

    @Test
    void theServerProducesTheConnectionEventsAndRefusesTheOnesTheEdgesReport() {
        var lost = DeviceEvent.byServer("srv-edge-3-1", EventType.EDGE_DISCONNECTED, 3L, 30L, 300L, null, NOW, "sin reporte");

        assertThat(lost.severity()).isEqualTo(EventSeverity.IMPORTANT);
        assertThat(lost.origin()).isEqualTo(EventOrigin.LIVE);
        assertThat(lost.receivedAt()).isEqualTo(NOW);
        assertThat(DeviceEvent.byServer("srv-edge-3-2", EventType.EDGE_RECONNECTED, 3L, 30L, 300L, null, NOW, null)
                .severity()).isEqualTo(EventSeverity.INFORMATIVE);
        assertThatThrownBy(() -> DeviceEvent.byServer("x", EventType.HELP_REQUESTED, 3L, 30L, 300L, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceEvent.byServer("x", EventType.DEVICE_DISCONNECTED, 3L, 30L, 300L, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceEvent.byServer("bad id", EventType.EDGE_DISCONNECTED, 3L, 30L, 300L, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
