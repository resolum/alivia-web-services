package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.queries.GetDeviceTelemetryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceTelemetryPageView;

/**
 * Answers the diagnostics readings of a device. It does not decide who may ask: it is for technicians and
 * administrators, and the endpoint checks the role.
 */
public interface DeviceTelemetryQueryService {

    /**
     * The readings of the device in the period, newest first, only the ones of the home where it is placed now.
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery when the period or the page is not valid
     * @throws com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible when the device does not exist or is not
     *         placed in a home
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when the readings cannot be read
     */
    DeviceTelemetryPageView handle(GetDeviceTelemetryQuery query);
}
