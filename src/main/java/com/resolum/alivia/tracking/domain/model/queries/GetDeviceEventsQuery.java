package com.resolum.alivia.tracking.domain.model.queries;

import java.time.Instant;

/**
 * The history of a device, asked on behalf of a user. What the client sent is kept as it came (absent or text) and
 * validated by the service, which is the one that knows the limits.
 *
 * @param userId   the authenticated user, who must be able to read the home of the device
 * @param deviceId the device
 * @param from     the start of the period, included; absent means the longest period back from {@code to}
 * @param to       the end of the period, excluded; absent means now
 * @param type     the type of event, if the history is to show only one
 * @param page     the page, from 0; absent means 0
 * @param size     the size of the page; absent means the default
 */
public record GetDeviceEventsQuery(Long userId, Long deviceId, Instant from, Instant to, String type, Integer page,
                                   Integer size) {
}
