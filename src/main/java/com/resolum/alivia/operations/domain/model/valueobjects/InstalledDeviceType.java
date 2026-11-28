package com.resolum.alivia.operations.domain.model.valueobjects;

/** Kind of IoT module the technician installs in the home, so the hub can map it. */
public enum InstalledDeviceType {
    MICROPHONE_EDGE,
    ACTUATOR_EDGE,
    MICROPHONE,
    DOOR,
    WINDOW,
    CURTAIN,
    LIGHT,
    /** Legacy values kept only so historical orders remain readable. */
    ACTUATOR,
    SENSOR,
    HUB;

    public boolean isEdge() {
        return this == MICROPHONE_EDGE || this == ACTUATOR_EDGE;
    }

    public InstalledDeviceType requiredEdge() {
        return switch (this) {
            case MICROPHONE -> MICROPHONE_EDGE;
            case DOOR, WINDOW, CURTAIN, LIGHT -> ACTUATOR_EDGE;
            default -> null;
        };
    }
}
