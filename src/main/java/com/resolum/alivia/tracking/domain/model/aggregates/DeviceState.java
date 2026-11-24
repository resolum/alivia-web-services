package com.resolum.alivia.tracking.domain.model.aggregates;

import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedDeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.StateOutcome;
import com.resolum.alivia.tracking.domain.model.valueobjects.StateUpdate;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * The last reliable state of a device: what the family sees when it asks how the home is.
 *
 * <p>A report replaces it only when it is valid and was observed no earlier than the state already kept. A report that
 * arrives late after an outage ({@code STALE}) and one with invalid data ({@code REJECTED}) leave the kept state as it
 * is, so an old or corrupt reading never shows as the current one.</p>
 *
 * @param deviceId            the device
 * @param edgeId              the edge that reports it
 * @param householdId         the household of its installation
 * @param connectionStatus    whether the edge reaches the device
 * @param operationalStatus   how the device works
 * @param currentState        what it is doing ({@code open}, {@code closed}, {@code on}, {@code off}…), if it says
 * @param batteryPct          the battery left, 0 to 100, if the device measures it
 * @param lastCommunicationAt the last time the edge heard from the device
 * @param observedAt          when the edge observed this state
 * @param updatedAt           when the backend kept it
 * @param faults              the codes of the failures it reports
 */
public record DeviceState(Long deviceId, Long edgeId, Long householdId, ConnectionStatus connectionStatus,
                          OperationalStatus operationalStatus, String currentState, Integer batteryPct,
                          Instant lastCommunicationAt, Instant observedAt, Instant updatedAt, List<String> faults) {

    private static final int MAX_STATE = 32;
    private static final int MAX_FAULTS = 20;
    private static final int MAX_FAULT = 64;

    public DeviceState {
        faults = faults == null ? List.of() : List.copyOf(faults);
    }

    /**
     * Decides what a report does to the state kept.
     *
     * @param current the state kept, or {@code null} when the device never reported
     * @param now     when the backend received the report
     */
    public static StateUpdate evaluate(DeviceState current, ReportingContext context, ReportedDeviceState report,
                                       Instant now, TrackingPolicy policy) {
        var rejection = rejectionOf(context, report, now, policy);
        if (rejection != null) {
            return new StateUpdate(StateOutcome.REJECTED, rejection, current, EventAttributes.empty());
        }
        if (current != null && report.observedAt().isBefore(current.observedAt())) {
            return new StateUpdate(StateOutcome.STALE, null, current, EventAttributes.empty());
        }
        var state = new DeviceState(report.deviceId(), context.edgeId(), context.householdId(),
                parse(ConnectionStatus.class, report.connectionStatus()),
                parse(OperationalStatus.class, report.operationalStatus()),
                blankToNull(report.currentState()), report.batteryPct(), report.lastCommunicationAt(),
                report.observedAt(), now, report.faults());
        return new StateUpdate(StateOutcome.APPLIED, null, state, EventAttributes.of(report.diagnostics(), policy));
    }

    /** Whether this state is worse than the one before it: it went from working to failing or to disconnected. */
    public boolean worsenedFrom(DeviceState before) {
        if (before == null) return false;
        boolean nowDown = connectionStatus == ConnectionStatus.DISCONNECTED || operationalStatus == OperationalStatus.FAILED;
        boolean beforeDown = before.connectionStatus == ConnectionStatus.DISCONNECTED
                || before.operationalStatus == OperationalStatus.FAILED;
        return nowDown && !beforeDown;
    }

    /** Whether the device came back after being disconnected or failing. */
    public boolean recoveredFrom(DeviceState before) {
        return before != null && before.isDown() && !isDown();
    }

    private boolean isDown() {
        return connectionStatus == ConnectionStatus.DISCONNECTED || operationalStatus == OperationalStatus.FAILED;
    }

    private static RejectionReason rejectionOf(ReportingContext context, ReportedDeviceState report, Instant now,
                                               TrackingPolicy policy) {
        if (report.deviceId() == null || !context.knows(report.deviceId())) return RejectionReason.UNKNOWN_DEVICE;
        if (parse(ConnectionStatus.class, report.connectionStatus()) == null
                || parse(OperationalStatus.class, report.operationalStatus()) == null) {
            return RejectionReason.INVALID_STATUS;
        }
        if (report.batteryPct() != null && (report.batteryPct() < 0 || report.batteryPct() > 100)) {
            return RejectionReason.BATTERY_OUT_OF_RANGE;
        }
        var limit = now.plus(policy.clockTolerance());
        if (report.observedAt() == null || report.observedAt().isAfter(limit)
                || (report.lastCommunicationAt() != null && report.lastCommunicationAt().isAfter(limit))) {
            return RejectionReason.INVALID_TIMESTAMP;
        }
        if (report.currentState() != null && report.currentState().strip().length() > MAX_STATE) {
            return RejectionReason.INVALID_STATUS;
        }
        var faults = report.faults();
        if (faults != null && (faults.size() > MAX_FAULTS
                || faults.stream().anyMatch(fault -> fault == null || fault.isBlank() || fault.length() > MAX_FAULT))) {
            return RejectionReason.INVALID_STATUS;
        }
        try {
            EventAttributes.of(report.diagnostics(), policy);
        } catch (com.resolum.alivia.tracking.domain.exceptions.InvalidTrackingData invalid) {
            return invalid.reason();
        }
        return null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String text) {
        if (text == null) return null;
        try {
            return Enum.valueOf(type, text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
