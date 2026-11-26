package com.resolum.alivia.tracking.interfaces.acl;

import java.time.Instant;
import java.util.List;

/**
 * Anti-corruption layer for other bounded contexts that need what Tracking knows. It answers with primitives and java
 * types only.
 *
 * <p>It does not decide who may see a home: the consumer checks that first, with the facade of the context that knows who
 * takes care of whom.</p>
 */
public interface TrackingContextFacade {

    /**
     * What the people of a home asked their devices to do in a period and how each command ended, each command counted
     * once however many events or resends there were about it. It carries counts only: never audio, transcriptions or
     * text. A period without commands is a summary of zeros, not an error.
     *
     * <p>Commands are counted by the events that happened in the period. A command whose request is just before the
     * period and whose result is inside it counts as one whose result is known, not as a request of this period; the
     * difference matters only at the edges of the period.</p>
     *
     * @param householdId the home
     * @param deviceId    only the commands of this device; {@code null} for the whole home. A rejection that names no
     *                    device is not in a summary limited to a device
     * @param from        the start of the period, included
     * @param to          the end of the period, excluded
     * @throws IllegalArgumentException when the household or the period is missing, or the start is after the end
     * @throws RuntimeException         when the events cannot be read: an empty summary is never answered for a failed read
     */
    AutonomySummary summarizeAutonomy(Long householdId, Long deviceId, Instant from, Instant to);

    /**
     * @param householdId          the home
     * @param deviceId             the device it is limited to, or {@code null}
     * @param from                 the start of the period, included
     * @param to                   the end of the period, excluded
     * @param total                the counts of the whole period
     * @param byDevice             the counts of each device that had commands, by device id
     * @param withoutDevice        the commands that name no device
     * @param edgeLink             {@code ONLINE}, {@code OFFLINE} (at least one edge of the home is not heard) or
     *                             {@code UNKNOWN} (none has ever reported)
     * @param lastUpdatedAt        the latest report of any edge of the home, the date of the last update the counts can
     *                             rely on; {@code null} when none has ever reported
     * @param mayHavePendingEvents whether events may be waiting in an edge, so the counts may still grow
     */
    record AutonomySummary(Long householdId, Long deviceId, Instant from, Instant to, AutonomyCounts total,
                           List<DeviceAutonomy> byDevice, AutonomyCounts withoutDevice, String edgeLink,
                           Instant lastUpdatedAt, boolean mayHavePendingEvents) {
    }

    /**
     * How many commands there were and how each one ended; each command is in exactly one of the six outcomes, so
     * {@code requested} is their sum.
     *
     * @param executedLocally of the commands the actuator ran (succeeded or failed), those that were executed without
     *                        connection and reached the backend later
     */
    record AutonomyCounts(int requested, int succeeded, int failed, int rejectedLowConfidence, int rejectedUnauthorized,
                          int noConfirmation, int inProgress, int executedLocally) {
    }

    /** The commands of one device. */
    record DeviceAutonomy(Long deviceId, AutonomyCounts counts) {
    }
}
