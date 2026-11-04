package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.Instant;

public record IncidentStatusChangeResource(String status, Instant changedAt) {
}
