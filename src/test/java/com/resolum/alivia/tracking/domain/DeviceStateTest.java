package com.resolum.alivia.tracking.domain;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.StateOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The rule of the last reliable state: a late or invalid report never replaces it. */
class DeviceStateTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:04:00Z");
    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();
    private static final ReportingContext CONTEXT = new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L, 12L));

    private static ReportedDeviceState report(Long deviceId, Instant observedAt) {
        return new ReportedDeviceState(deviceId, "CONNECTED", "OK", "closed", 78, observedAt, observedAt, List.of(), null);
    }

    private static ReportedDeviceState with(ReportedDeviceState r, String connection, String operational, String state,
                                            Integer battery, Instant lastCommunication, Instant observedAt,
                                            List<String> faults, Map<String, Object> diagnostics) {
        return new ReportedDeviceState(r.deviceId(), connection, operational, state, battery, lastCommunication,
                observedAt, faults, diagnostics);
    }

    @Test
    void theFirstValidReportBecomesTheState() {
        var update = DeviceState.evaluate(null, CONTEXT, report(10L, NOW.minusSeconds(2)), NOW, POLICY);

        assertThat(update.outcome()).isEqualTo(StateOutcome.APPLIED);
        assertThat(update.reason()).isNull();
        var state = update.state();
        assertThat(state.deviceId()).isEqualTo(10L);
        assertThat(state.edgeId()).isEqualTo(3L);
        assertThat(state.householdId()).isEqualTo(300L);
        assertThat(state.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(state.operationalStatus()).isEqualTo(OperationalStatus.OK);
        assertThat(state.currentState()).isEqualTo("closed");
        assertThat(state.batteryPct()).isEqualTo(78);
        assertThat(state.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aNewerOrEqualReportReplacesTheState() {
        var kept = DeviceState.evaluate(null, CONTEXT, report(10L, NOW.minusSeconds(60)), NOW, POLICY).state();

        var newer = DeviceState.evaluate(kept, CONTEXT, with(report(10L, NOW.minusSeconds(5)), "CONNECTED", "OK",
                "open", 70, NOW.minusSeconds(5), NOW.minusSeconds(5), List.of(), null), NOW, POLICY);
        assertThat(newer.outcome()).isEqualTo(StateOutcome.APPLIED);
        assertThat(newer.state().currentState()).isEqualTo("open");

        var same = DeviceState.evaluate(kept, CONTEXT, report(10L, NOW.minusSeconds(60)), NOW, POLICY);
        assertThat(same.outcome()).isEqualTo(StateOutcome.APPLIED);
    }

    @Test
    void aLateReportAfterAnOutageIsStaleAndKeepsTheStateThatIsThere() {
        // The state of the fixture: the edge reports a state observed an hour before the one already kept.
        var kept = DeviceState.evaluate(null, CONTEXT, report(11L, NOW.minusSeconds(10)), NOW, POLICY).state();

        var late = DeviceState.evaluate(kept, CONTEXT, with(report(11L, NOW.minus(Duration.ofHours(1))), "CONNECTED",
                "OK", "open", null, null, NOW.minus(Duration.ofHours(1)), List.of(), null), NOW, POLICY);

        assertThat(late.outcome()).isEqualTo(StateOutcome.STALE);
        assertThat(late.reason()).isNull();
        assertThat(late.state()).isSameAs(kept);
        assertThat(late.diagnostics().isEmpty()).isTrue();
    }

    @Test
    void anInvalidReportIsRejectedWithItsReasonAndKeepsTheLastReliableState() {
        var kept = DeviceState.evaluate(null, CONTEXT, report(12L, NOW.minusSeconds(10)), NOW, POLICY).state();
        var base = report(12L, NOW.minusSeconds(1));

        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", 101, null, NOW, List.of(), null),
                RejectionReason.BATTERY_OUT_OF_RANGE);
        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", -1, null, NOW, List.of(), null),
                RejectionReason.BATTERY_OUT_OF_RANGE);
        assertRejected(kept, with(base, "ONLINE", "OK", "closed", 50, null, NOW, List.of(), null),
                RejectionReason.INVALID_STATUS);
        assertRejected(kept, with(base, "CONNECTED", null, "closed", 50, null, NOW, List.of(), null),
                RejectionReason.INVALID_STATUS);
        assertRejected(kept, with(base, "CONNECTED", "OK", "x".repeat(33), 50, null, NOW, List.of(), null),
                RejectionReason.INVALID_STATUS);
        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", 50, null, NOW, List.of(" "), null),
                RejectionReason.INVALID_STATUS);
        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", 50, null, null, List.of(), null),
                RejectionReason.INVALID_TIMESTAMP);
        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", 50, null, NOW.plus(Duration.ofMinutes(6)),
                List.of(), null), RejectionReason.INVALID_TIMESTAMP);
        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", 50, NOW.plus(Duration.ofHours(1)), NOW,
                List.of(), null), RejectionReason.INVALID_TIMESTAMP);
        assertRejected(kept, with(base, "CONNECTED", "OK", "closed", 50, null, NOW, List.of(),
                Map.of("audio", "x")), RejectionReason.FORBIDDEN_ATTRIBUTE);
    }

    private static void assertRejected(DeviceState kept, ReportedDeviceState report, RejectionReason reason) {
        var update = DeviceState.evaluate(kept, CONTEXT, report, NOW, POLICY);

        assertThat(update.outcome()).isEqualTo(StateOutcome.REJECTED);
        assertThat(update.reason()).isEqualTo(reason);
        assertThat(update.state()).isSameAs(kept);
    }

    @Test
    void aDeviceOutsideTheTableOfTheInstallationIsRejected() {
        var update = DeviceState.evaluate(null, CONTEXT, report(99L, NOW), NOW, POLICY);

        assertThat(update.outcome()).isEqualTo(StateOutcome.REJECTED);
        assertThat(update.reason()).isEqualTo(RejectionReason.UNKNOWN_DEVICE);
        assertThat(update.state()).isNull();
        assertThat(DeviceState.evaluate(null, CONTEXT, report(null, NOW), NOW, POLICY).reason())
                .isEqualTo(RejectionReason.UNKNOWN_DEVICE);
    }

    @Test
    void theDiagnosticsOfAnAppliedReportAreKeptApartAsASample() {
        var withDiagnostics = with(report(10L, NOW.minusSeconds(1)), "CONNECTED", "OK", "closed", 78, null,
                NOW.minusSeconds(1), List.of(), Map.of("rssi", -55, "freeHeap", 180000));

        var update = DeviceState.evaluate(null, CONTEXT, withDiagnostics, NOW, POLICY);

        assertThat(update.diagnostics().values()).containsEntry("rssi", -55).containsEntry("freeHeap", 180000);
    }

    @Test
    void aDeviceThatStopsWorkingAndComesBackIsDetectedFromTheChangeOfState() {
        var working = DeviceState.evaluate(null, CONTEXT, report(10L, NOW.minusSeconds(60)), NOW, POLICY).state();
        var down = DeviceState.evaluate(working, CONTEXT, with(report(10L, NOW.minusSeconds(30)), "DISCONNECTED", "OK",
                null, null, null, NOW.minusSeconds(30), List.of("servo_stuck"), null), NOW, POLICY).state();
        var failed = DeviceState.evaluate(working, CONTEXT, with(report(10L, NOW.minusSeconds(30)), "CONNECTED", "FAILED",
                null, null, null, NOW.minusSeconds(30), List.of(), null), NOW, POLICY).state();
        var back = DeviceState.evaluate(down, CONTEXT, report(10L, NOW.minusSeconds(1)), NOW, POLICY).state();

        assertThat(down.worsenedFrom(working)).isTrue();
        assertThat(failed.worsenedFrom(working)).isTrue();
        assertThat(down.worsenedFrom(null)).isFalse();
        assertThat(back.worsenedFrom(down)).isFalse();
        // Already down and still down is not a new worsening.
        assertThat(failed.worsenedFrom(down)).isFalse();
        assertThat(back.recoveredFrom(down)).isTrue();
        assertThat(working.recoveredFrom(null)).isFalse();
        assertThat(working.recoveredFrom(working)).isFalse();
        assertThat(down.faults()).containsExactly("servo_stuck");
    }
}
