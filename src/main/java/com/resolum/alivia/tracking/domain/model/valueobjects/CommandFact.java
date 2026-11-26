package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;

/**
 * What the autonomy metrics need to know of an event about a command: nothing of audio, transcription or detail.
 *
 * @param commandId  the command the event is about
 * @param type       {@code COMMAND_REQUESTED}, {@code COMMAND_RESULT} or {@code COMMAND_REJECTED}
 * @param result     how it ended; {@code null} for a request
 * @param origin     {@code SYNCED} when it was executed without connection and reached the backend later
 * @param occurredAt when it happened
 * @param deviceId   the device involved; {@code null} when the event does not name one
 */
public record CommandFact(String commandId, EventType type, EventResult result, EventOrigin origin, Instant occurredAt,
                          Long deviceId) {
}
