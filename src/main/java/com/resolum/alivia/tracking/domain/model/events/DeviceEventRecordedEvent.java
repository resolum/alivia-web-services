package com.resolum.alivia.tracking.domain.model.events;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;

/**
 * An event of a home was kept for the first time (a repeated report is not published again). The ones who follow the
 * home live, the mobile app among them, are told from here.
 *
 * @param event the event as it was kept
 */
public record DeviceEventRecordedEvent(DeviceEvent event) {
}
