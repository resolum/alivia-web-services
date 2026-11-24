package com.resolum.alivia.tracking.application.internal.outboundservices.acl;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
}
