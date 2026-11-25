package com.resolum.alivia.tracking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What became of one event of the batch.
 *
 * @param eventId the id the edge gave the event
 * @param status  {@code ACCEPTED}, {@code DUPLICATE} or {@code REJECTED}
 * @param reason  the code of the rejection; absent otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventOutcomeResource(String eventId, String status, String reason) {
}
