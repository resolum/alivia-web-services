package com.resolum.alivia.assets.application.internal.outboundservices.edge;

import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent.Cause;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;

/**
 * What the edge services of an installation need to know after the devices of their household changed: the complete
 * table of devices after the change, with its version, so the edge replaces its copy and ignores any older table.
 *
 * @param cause          what changed
 * @param installationId installation whose edge services must synchronize
 * @param householdId    household of that installation
 * @param deviceId       device the change is about
 * @param table          the whole table of devices after the change
 */
public record EdgeSyncMessage(Cause cause, Long installationId, Long householdId, Long deviceId, EdgeAccessTable table) {
}
