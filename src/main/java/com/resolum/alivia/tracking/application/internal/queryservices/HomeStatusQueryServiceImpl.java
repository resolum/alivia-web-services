package com.resolum.alivia.tracking.application.internal.queryservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.HomeDevice;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.queries.GetHomeStatusByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceStatusView;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeStatusView;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeLinkSummary;
import com.resolum.alivia.tracking.domain.model.valueobjects.HomeStatusView;
import com.resolum.alivia.tracking.domain.repositories.DeviceStateRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.domain.services.HomeStatusQueryService;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link HomeStatusQueryService}.
 *
 * <p>The devices come from Assets (what is placed in the home right now) and their state from Tracking, so a device that
 * never reported is listed, and the state of a device that was moved from another home is not shown here: only the
 * states kept for this household count.</p>
 */
@Slf4j
@Service
public class HomeStatusQueryServiceImpl implements HomeStatusQueryService {

    private final TrackingAccessService access;
    private final ExternalAssetsService assets;
    private final DeviceStateRepository states;
    private final EdgeStatusRepository edgeStatuses;
    private final MeterRegistry meters;

    public HomeStatusQueryServiceImpl(TrackingAccessService access, ExternalAssetsService assets,
                                      DeviceStateRepository states, EdgeStatusRepository edgeStatuses,
                                      MeterRegistry meters) {
        this.access = access;
        this.assets = assets;
        this.states = states;
        this.edgeStatuses = edgeStatuses;
        this.meters = meters;
    }

    @Override
    public HomeStatusView handle(GetHomeStatusByAssistedPersonQuery query) {
        var householdId = access.requireReadableHouseholdOfPerson(query.userId(), query.assistedPersonId());
        var devices = assets.fetchDevicesOfHousehold(householdId).stream()
                .sorted(Comparator.comparing(HomeDevice::deviceId)).toList();

        var stateOf = devices.isEmpty() ? Map.<Long, DeviceState>of()
                : read(() -> states.findAllByDeviceIds(devices.stream().map(HomeDevice::deviceId).toList())).stream()
                // A device that was in another home keeps the state it had there; it is not of this one.
                .filter(state -> Objects.equals(state.householdId(), householdId))
                .collect(Collectors.toMap(DeviceState::deviceId, state -> state, (first, second) -> first));
        var edges = read(() -> edgeStatuses.findAllByHouseholdId(householdId));

        // An edge that is no longer assigned to the home does not count: the state of its devices stays as the last known
        // one, marked stale because nobody updates it, and its silence is not the silence of the home.
        var assignedEdges = assets.fetchAssignedEdges(householdId);
        var assigned = assignedEdges.stream().map(ExternalAssetsService.HomeEdge::edgeId).collect(Collectors.toUnmodifiableSet());
        var home = HomeLinkSummary.of(edges, assigned);
        var views = devices.stream()
                .map(device -> view(device, stateOf.get(device.deviceId()), home.silentEdgeIds(), assigned)).toList();
        var lastUpdatedAt = Stream.concat(Stream.ofNullable(home.lastReportAt()),
                        stateOf.values().stream().filter(state -> assigned.contains(state.edgeId())).map(DeviceState::updatedAt))
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        var statusOf = edges.stream().collect(Collectors.toMap(EdgeStatus::edgeId, edge -> edge, (first, second) -> first));
        var edgeViews = assignedEdges.stream().map(edge -> {
            var status = statusOf.get(edge.edgeId());
            return status == null ? new EdgeStatusView(edge.edgeId(), edge.kind(), "UNKNOWN", null, null)
                    : new EdgeStatusView(edge.edgeId(), edge.kind(), status.link().name(), status.lastReportAt(), status.offlineSince());
        }).toList();
        return new HomeStatusView(householdId, home.link(), home.offlineSince(), lastUpdatedAt,
                home.mayHavePendingEvents(), views, edgeViews);
    }

    private static DeviceStatusView view(HomeDevice device, DeviceState state, Set<Long> silentEdges, Set<Long> assigned) {
        if (state == null) {
            return new DeviceStatusView(device.deviceId(), device.alias(), device.type(), device.roomKey(),
                    ConnectionStatus.UNKNOWN, null, null, null, null, null, null, List.of(), false);
        }
        return new DeviceStatusView(device.deviceId(), device.alias(), device.type(), device.roomKey(),
                state.connectionStatus(), state.operationalStatus(), state.currentState(), state.batteryPct(),
                state.lastCommunicationAt(), state.observedAt(), state.updatedAt(), state.faults(),
                silentEdges.contains(state.edgeId()) || !assigned.contains(state.edgeId()));
    }

    /** A read that fails is not an empty home: the caller is told the data is not available. */
    private <T> T read(Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException failure) {
            meters.counter("tracking.storage.failures", "operation", "read_home_status").increment();
            log.error("The state of a home could not be read.", failure);
            throw TrackingStorageUnavailable.forRead(failure);
        }
    }
}
