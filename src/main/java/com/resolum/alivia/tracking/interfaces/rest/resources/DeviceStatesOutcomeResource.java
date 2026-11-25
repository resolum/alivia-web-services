package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.util.List;

/** The outcome of every device of the report, in the same order. */
public record DeviceStatesOutcomeResource(List<DeviceStateOutcomeResource> results) {
}
