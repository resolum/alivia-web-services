package com.resolum.alivia.tracking.domain.model.valueobjects;

/** How much an event matters to who follows the home (RNF04). The server decides it; the edge never sends it. */
public enum EventSeverity {
    INFORMATIVE,
    PREVENTIVE,
    IMPORTANT,
    CRITICAL
}
