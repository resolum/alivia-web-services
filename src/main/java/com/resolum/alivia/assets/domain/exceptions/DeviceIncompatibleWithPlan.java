package com.resolum.alivia.assets.domain.exceptions;

/** The device does not fit what the household contracted. */
public class DeviceIncompatibleWithPlan extends RuntimeException {
    public DeviceIncompatibleWithPlan(String reason) {
        super(reason);
    }
}
