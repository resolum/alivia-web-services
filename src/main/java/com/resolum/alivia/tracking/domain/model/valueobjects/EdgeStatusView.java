package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;

/**
 * One edge assigned to the home and whether it is heard.
 *
 * @param kind         {@code MICROPHONE} (the voice edge) or {@code ACTUATOR}
 * @param link         {@code ONLINE}, {@code OFFLINE} or {@code UNKNOWN} when it has never reported
 * @param lastReportAt when the backend last heard from it; {@code null} when it never reported
 * @param offlineSince since when it is not heard; {@code null} while online or when it never reported
 */
public record EdgeStatusView(Long edgeId, String kind, String link, Instant lastReportAt, Instant offlineSince) {
}
