package com.resolum.alivia.tracking.application.internal.queryservices;

import com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.queries.GetDeviceEventsQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceEventsPageView;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.DeviceEventsQueryService;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Implementation of {@link DeviceEventsQueryService}.
 *
 * <p>The request is validated before anything is read, and the home it is about is the one where the device is placed
 * now, so the history a device had in another home does not follow it into this one.</p>
 */
@Slf4j
@Service
public class DeviceEventsQueryServiceImpl implements DeviceEventsQueryService {

    private final TrackingAccessService access;
    private final DeviceEventRepository events;
    private final HistoryPolicy policy;
    private final Clock clock;
    private final MeterRegistry meters;

    public DeviceEventsQueryServiceImpl(TrackingAccessService access, DeviceEventRepository events, HistoryPolicy policy,
                                        Clock clock, MeterRegistry meters) {
        this.access = access;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public DeviceEventsPageView handle(GetDeviceEventsQuery query) {
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? policy.defaultPageSize() : query.size();
        if (page < 0) {
            throw new InvalidHistoryQuery("invalid_page", "The page must be 0 or more.");
        }
        if (size < 1 || size > policy.maxPageSize()) {
            throw new InvalidHistoryQuery("invalid_page_size", "The size must be between 1 and " + policy.maxPageSize() + ".");
        }
        var type = typeOf(query.type());
        var to = query.to() == null ? clock.instant() : query.to();
        var from = query.from() == null ? to.minus(policy.maxRange()) : query.from();
        if (from.isAfter(to)) {
            throw new InvalidHistoryQuery("invalid_range", "The start of the period cannot be after its end.");
        }
        if (Duration.between(from, to).compareTo(policy.maxRange()) > 0) {
            throw new InvalidHistoryQuery("range_too_large",
                    "The period cannot be longer than " + policy.maxRange().toDays() + " days.");
        }

        var householdId = access.requireReadableHouseholdOfDevice(query.userId(), query.deviceId());
        EventPage found;
        try {
            found = events.findByDevice(householdId, query.deviceId(), from, to, type, page, size);
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "read_device_events").increment();
            log.error("The events of the device {} could not be read.", query.deviceId(), failure);
            throw new TrackingStorageUnavailable(failure);
        }
        return new DeviceEventsPageView(query.deviceId(), from, to, page, size, found.total(), found.items());
    }

    private static EventType typeOf(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return EventType.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidHistoryQuery("invalid_type", "The type of event is not known.");
        }
    }
}
