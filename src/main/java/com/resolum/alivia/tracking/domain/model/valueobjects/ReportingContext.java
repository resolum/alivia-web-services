package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.util.Set;

/**
 * Where an edge reports from: its installation and household, and the devices of the installation table. It is what the
 * server knows about the edge from its credential, never what the edge says about itself.
 *
 * @param edgeId         the edge that reports
 * @param installationId its installation
 * @param householdId    the household of the installation
 * @param deviceIds      the devices of the table of the installation
 */
public record ReportingContext(Long edgeId, Long installationId, Long householdId, Set<Long> deviceIds) {

    public ReportingContext {
        if (edgeId == null || installationId == null || householdId == null) {
            throw new IllegalArgumentException("The edge, its installation and its household are required.");
        }
        deviceIds = deviceIds == null ? Set.of() : Set.copyOf(deviceIds);
    }

    public boolean knows(Long deviceId) {
        return deviceId != null && deviceIds.contains(deviceId);
    }
}
