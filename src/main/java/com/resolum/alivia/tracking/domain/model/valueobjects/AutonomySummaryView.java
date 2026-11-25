package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;

/**
 * The autonomy of the people of a home in a period: what they asked for and how it ended. It carries counts only: no
 * audio, no transcription, no free text.
 *
 * @param householdId          the home
 * @param deviceId             the device the summary is limited to; {@code null} when it covers the whole home
 * @param from                 the start of the period, included
 * @param to                   the end of the period, excluded
 * @param total                the counts of the whole period
 * @param byDevice             the counts of each device that had commands, by device id; those that name no device are
 *                             apart in {@code withoutDevice}
 * @param withoutDevice        the commands that name no device
 * @param link                 whether the edges of the home are heard now
 * @param lastUpdatedAt        the latest report of any edge of the home; {@code null} when none has ever reported
 * @param mayHavePendingEvents whether events may be waiting in an edge, so the counts may still grow
 */
public record AutonomySummaryView(Long householdId, Long deviceId, Instant from, Instant to, AutonomyCounts total,
                                  List<DeviceAutonomy> byDevice, AutonomyCounts withoutDevice, HomeLink link,
                                  Instant lastUpdatedAt, boolean mayHavePendingEvents) {

    public AutonomySummaryView {
        byDevice = byDevice == null ? List.of() : List.copyOf(byDevice);
    }
}
