package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

/** What the technician needs of the incident behind a maintenance order. */
public record IncidentSummaryResource(Long id, String code, String status, String problemType, String description,
                                      Instant occurredAt, ReportedDeviceResource device, List<String> evidenceUrls) {
}
