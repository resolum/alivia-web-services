package com.resolum.alivia.tracking.application.internal.queryservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.queries.GetDeviceTelemetryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceTelemetryPageView;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.TelemetryPage;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import com.resolum.alivia.tracking.domain.services.DeviceTelemetryQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Implementation of {@link DeviceTelemetryQueryService}.
 *
 * <p>The period and the page follow the same limits as the history of events (D-TR-9). The readings are the ones of the
 * home where the device is placed now, as everywhere in Tracking, so a technician does not see what the device reported in
 * a home it left. Who may ask is a role of the account, decided by the endpoint.</p>
 */
@Slf4j
@Service
public class DeviceTelemetryQueryServiceImpl implements DeviceTelemetryQueryService {

    private final ExternalAssetsService assets;
    private final TelemetrySampleRepository samples;
    private final HistoryPolicy policy;
    private final Clock clock;
    private final MeterRegistry meters;

    public DeviceTelemetryQueryServiceImpl(ExternalAssetsService assets, TelemetrySampleRepository samples,
                                           HistoryPolicy policy, Clock clock, MeterRegistry meters) {
        this.assets = assets;
        this.samples = samples;
        this.policy = policy;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public DeviceTelemetryPageView handle(GetDeviceTelemetryQuery query) {
        var window = policy.window(query.from(), query.to(), query.page(), query.size(), clock.instant());
        var householdId = assets.fetchHouseholdOfDevice(query.deviceId())
                .orElseThrow(() -> new DeviceNotAccessible(query.deviceId()));
        TelemetryPage found;
        try {
            found = samples.findByDevice(householdId, query.deviceId(), window.from(), window.to(), window.page(),
                    window.size());
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "read_device_telemetry").increment();
            log.error("The telemetry of the device {} could not be read.", query.deviceId(), failure);
            throw TrackingStorageUnavailable.forRead(failure);
        }
        return new DeviceTelemetryPageView(query.deviceId(), window.from(), window.to(), window.page(), window.size(),
                found.total(), found.items());
    }
}
