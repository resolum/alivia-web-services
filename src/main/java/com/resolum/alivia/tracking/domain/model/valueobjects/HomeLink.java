package com.resolum.alivia.tracking.domain.model.valueobjects;

/** Whether the backend hears from the edges of a home, taken together. */
public enum HomeLink {
    /** Every edge of the home has reported within the allowed silence. */
    ONLINE,
    /** At least one edge of the home is not heard. */
    OFFLINE,
    /** No edge of the home has ever reported. */
    UNKNOWN
}
