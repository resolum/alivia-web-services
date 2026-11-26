package com.resolum.alivia.tracking.interfaces.rest.transform;

import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceTelemetryPageView;
import com.resolum.alivia.tracking.interfaces.rest.resources.DeviceTelemetryPageResource;
import com.resolum.alivia.tracking.interfaces.rest.resources.TelemetrySampleResource;

/** From a page of readings to what the API shows. */
public final class DeviceTelemetryPageResourceAssembler {

    private DeviceTelemetryPageResourceAssembler() {
    }

    public static DeviceTelemetryPageResource toResource(DeviceTelemetryPageView view) {
        return new DeviceTelemetryPageResource(view.deviceId(), view.from(), view.to(), view.page(), view.size(),
                view.total(), view.totalPages(), view.items().stream()
                .map(sample -> new TelemetrySampleResource(sample.edgeId(), sample.sampledAt(), sample.metrics().values()))
                .toList());
    }
}
