package com.resolum.alivia.assets.domain.model.valueobjects;

/** What an edge service reports from the home. */
public enum EdgeEventType {
    /** The assisted person asked for help (by voice or with the button of the device). */
    HELP_REQUESTED,
    /** The battery of an embedded device is running low. */
    LOW_BATTERY,
    /** An actuator did not do what it was told, or its embedded device stopped answering. */
    DEVICE_FAILURE
}
