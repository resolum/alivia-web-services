package com.resolum.alivia.tracking.domain.model.queries;

import java.time.Instant;

/**
 * The diagnostics readings of a device, asked by a technician or an administrator. What the client sent is kept as it
 * came and validated by the service.
 *
 * @param deviceId the device
 * @param from     the start of the period, included; absent means the longest period back from {@code to}
 * @param to       the end of the period, excluded; absent means now
 * @param page     the page, from 0; absent means 0
 * @param size     the size of the page; absent means the default
 */
public record GetDeviceTelemetryQuery(Long deviceId, Instant from, Instant to, Integer page, Integer size) {
}
