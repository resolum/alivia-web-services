package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One edge of the home and whether it is heard.
 *
 * @param kind         {@code MICROPHONE} (the voice edge) or {@code ACTUATOR}
 * @param link         {@code ONLINE}, {@code OFFLINE} or {@code UNKNOWN} when it has never reported
 * @param lastReportAt when it last reported; absent when it never did
 * @param offlineSince since when it is not heard; absent while online
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeEdgeStatusResource(Long edgeId,
                                     @Schema(allowableValues = {"MICROPHONE", "ACTUATOR"}) String kind,
                                     @Schema(allowableValues = {"ONLINE", "OFFLINE", "UNKNOWN"}) String link,
                                     Instant lastReportAt, Instant offlineSince) {
}
