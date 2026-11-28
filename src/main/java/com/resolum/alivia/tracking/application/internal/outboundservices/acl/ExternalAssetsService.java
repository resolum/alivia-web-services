package com.resolum.alivia.tracking.application.internal.outboundservices.acl;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outbound ACL service for asking Assets where an edge reports from. The answer is what the server knows about the edge
 * from its credential and the table of its installation, never what the edge says about itself.
 */
@Service("trackingExternalAssetsService")
public class ExternalAssetsService {

    private final AssetsContextFacade assetsContextFacade;

    public ExternalAssetsService(AssetsContextFacade assetsContextFacade) {
        this.assetsContextFacade = assetsContextFacade;
    }

    /**
     * The home an edge reports from.
     *
     * @param context       where it reports from, with the devices of the installation table
     * @param edgeKind      {@code MICROPHONE} or {@code ACTUATOR}
     * @param deviceAliases the name of each device of the installation, for the messages to the family
     */
    public record ReportingEdge(ReportingContext context, String edgeKind, Map<Long, String> deviceAliases) {

        public ReportingEdge {
            deviceAliases = deviceAliases == null ? Map.of() : Map.copyOf(deviceAliases);
        }
    }

    /** Empty when the edge is not assigned to an active installation. */
    public Optional<ReportingEdge> fetchReportingEdge(Long edgeId) {
        if (edgeId == null) return Optional.empty();
        return assetsContextFacade.fetchEdgeScope(edgeId).map(scope -> new ReportingEdge(
                new ReportingContext(scope.edgeId(), scope.installationId(), scope.householdId(),
                        new HashSet<>(scope.devices().stream().map(AssetsContextFacade.ScopedDevice::deviceId).toList())),
                scope.edgeKind(),
                scope.devices().stream().filter(device -> device.alias() != null)
                        .collect(Collectors.toMap(AssetsContextFacade.ScopedDevice::deviceId,
                                AssetsContextFacade.ScopedDevice::alias, (first, second) -> first))));
    }

    /**
     * A device of a home, with what a relative recognises it by.
     *
     * @param deviceId the device
     * @param alias    the name the business gave it; may be {@code null}
     * @param type     {@code MICROPHONE}, {@code DOOR}, {@code WINDOW}, {@code CURTAIN} or {@code LIGHT}
     * @param roomKey  the room where it is placed
     */
    public record HomeDevice(Long deviceId, String alias, String type, String roomKey) {
    }

    /** The devices placed in the household right now; empty when it has none. */
    public List<HomeDevice> fetchDevicesOfHousehold(Long householdId) {
        if (householdId == null) return List.of();
        return assetsContextFacade.fetchDevicesOfHousehold(householdId).stream()
                .map(device -> new HomeDevice(device.deviceId(), device.alias(), device.type(), device.roomKey()))
                .toList();
    }

    /**
     * @param kind {@code MICROPHONE} (the voice edge) or {@code ACTUATOR}
     */
    public record HomeEdge(Long edgeId, String kind) {
    }

    /** The edges assigned right now to the active installation of the household, with their kind; empty when none. */
    public List<HomeEdge> fetchAssignedEdges(Long householdId) {
        if (householdId == null) return List.of();
        return assetsContextFacade.fetchEdgesOfHousehold(householdId).stream()
                .map(edge -> new HomeEdge(edge.edgeId(), edge.edgeKind()))
                .sorted(Comparator.comparing(HomeEdge::edgeId)).toList();
    }

    /** The edges assigned right now to the active installation of the household; empty when it has none. */
    public Set<Long> fetchAssignedEdgeIds(Long householdId) {
        if (householdId == null) return Set.of();
        return assetsContextFacade.fetchEdgesOfHousehold(householdId).stream()
                .map(AssetsContextFacade.AssignedEdge::edgeId).collect(Collectors.toUnmodifiableSet());
    }

    /** The household where the device is placed right now; empty when it is not placed in an active installation. */
    public Optional<Long> fetchHouseholdOfDevice(Long deviceId) {
        if (deviceId == null) return Optional.empty();
        return assetsContextFacade.fetchHouseholdOfDevice(deviceId);
    }
}
