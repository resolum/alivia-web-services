package com.resolum.alivia.tracking.interfaces.rest.resources;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The events an edge sends together, in the order they happened. */
public record ReportEdgeEventsBatchResource(@NotEmpty List<@NotNull BatchEventResource> events) {
}
