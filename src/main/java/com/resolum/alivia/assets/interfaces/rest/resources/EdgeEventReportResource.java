package com.resolum.alivia.assets.interfaces.rest.resources;

/** How many relatives were notified now; 0 when the event had already been reported. */
public record EdgeEventReportResource(int notified) {
}
