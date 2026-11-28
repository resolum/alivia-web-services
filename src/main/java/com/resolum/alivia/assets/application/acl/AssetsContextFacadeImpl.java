package com.resolum.alivia.assets.application.acl;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgesQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceCountByBusinessIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCountByBusinessIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.domain.model.queries.GetDevicesOfHouseholdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeScopeByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgesOfHouseholdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetHouseholdOfDeviceQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AssetsContextFacadeImpl implements AssetsContextFacade {
    private final DeviceQueryService deviceQueryService;
    private final DeviceCommandService deviceCommandService;
    private final EdgeCommandService edgeCommandService;
    private final EdgeQueryService edgeQueryService;
    private final HouseholdDeviceRegistryCommandService householdDeviceRegistryCommandService;
    private final DeviceAssignmentCommandService deviceAssignmentCommandService;
    private final HouseholdDeviceRegistryQueryService householdDeviceRegistryQueryService;

    public AssetsContextFacadeImpl(DeviceQueryService deviceQueryService, DeviceCommandService deviceCommandService, EdgeCommandService edgeCommandService, EdgeQueryService edgeQueryService,
                                   HouseholdDeviceRegistryCommandService householdDeviceRegistryCommandService,
                                   DeviceAssignmentCommandService deviceAssignmentCommandService,
                                   HouseholdDeviceRegistryQueryService householdDeviceRegistryQueryService) {
        this.householdDeviceRegistryQueryService = householdDeviceRegistryQueryService;
        this.householdDeviceRegistryCommandService = householdDeviceRegistryCommandService;
        this.deviceAssignmentCommandService = deviceAssignmentCommandService;
        this.deviceQueryService = deviceQueryService;
        this.deviceCommandService = deviceCommandService;
        this.edgeCommandService = edgeCommandService;
        this.edgeQueryService = edgeQueryService;
    }


    @Override
    public Optional<InventoryAsset> fetchInventoryAssetByMacAddress(String macAddress) {
        var device = deviceQueryService.handle(new GetDeviceByMacAddressQuery(macAddress));
        if (device.isPresent()) {
            var value = device.get();
            return Optional.of(new InventoryAsset(value.getId(), value.getBusinessId().businessId(),
                    value.getMacAddress().macAddress(), value.getDeviceType().name(), value.getAssetStatus().name(), false));
        }
        return edgeQueryService.handle(new GetEdgeByMacAddressQuery(macAddress))
                .map(value -> new InventoryAsset(value.getId(), value.getBusinessId().businessId(),
                        value.getMacAddress().macAddress(), value.getEdgeType().name(), value.getAssetStatus().name(), true));
    }

    @Override
    public Optional<Long> fetchDeviceByMacAddress(String macAddress) {
        return deviceQueryService.handle(new GetDeviceByMacAddressQuery(macAddress)).map(Device::getId);
    }

    @Override
    public Optional<Long> fetchEdgeByMacAddress(String macAddress) {
        return edgeQueryService.handle(new GetEdgeByMacAddressQuery(macAddress)).map(Edge::getId);

    }

    @Override
    public Long registerInstallation(Long installationId, Long householdId, Long businessId) {
        return householdDeviceRegistryCommandService.handle(new RegisterInstallationCommand(installationId, householdId, businessId));
    }

    @Override
    public Long ensureInstallation(Long installationId, Long householdId, Long businessId) {
        return householdDeviceRegistryQueryService.handle(new GetInstallationEdgesQuery(installationId))
                .map(InstallationEdges::installationId)
                .orElseGet(() -> registerInstallation(installationId, householdId, businessId));
    }

    @Override
    public void placeEdge(Long installationId, Long edgeId) {
        householdDeviceRegistryCommandService.handle(new PlaceEdgeInInstallationCommand(installationId, edgeId));
    }

    @Override
    public void removeEdge(Long installationId, Long edgeId) {
        householdDeviceRegistryCommandService.handle(new RemoveEdgeFromInstallationCommand(installationId, edgeId));
    }

    @Override
    public boolean installationHasEdgeType(Long installationId, String edgeType) {
        return householdDeviceRegistryQueryService.handle(new GetInstallationEdgesQuery(installationId))
                .map(edges -> "MICROPHONE_EDGE".equals(edgeType) ? edges.microphoneEdgeId() != null : edges.actuatorEdgeId() != null)
                .orElse(false);
    }

    @Override
    public Long assignDevice(Long installationId, Long deviceId, String roomKey, String function, Long actingUserId) {
        return deviceAssignmentCommandService.handle(new AssignDeviceToInstallationCommand(installationId, deviceId, roomKey,
                function, actingUserId, CallerAuthority.AUTHORIZED_BY_CONTEXT)).getId();
    }

    @Override
    public void unassignDevice(Long deviceId, String reason, Long actingUserId) {
        deviceAssignmentCommandService.handle(new UnassignDeviceCommand(deviceId, reason, actingUserId,
                CallerAuthority.AUTHORIZED_BY_CONTEXT));
    }

    @Override
    public Optional<EdgeScope> fetchEdgeScope(Long edgeId) {
        return householdDeviceRegistryQueryService.handle(new GetEdgeScopeByEdgeIdQuery(edgeId))
                .map(view -> new EdgeScope(view.edgeId(),
                        view.edgeType() == EdgeType.MICROPHONE_EDGE ? "MICROPHONE" : "ACTUATOR",
                        view.installationId(), view.householdId(),
                        view.devices().stream()
                                .map(device -> new ScopedDevice(device.deviceId(), device.alias(), device.type().name(),
                                        device.status().name(), device.roomKey()))
                                .toList()));
    }

    @Override
    public List<ScopedDevice> fetchDevicesOfHousehold(Long householdId) {
        return householdDeviceRegistryQueryService.handle(new GetDevicesOfHouseholdQuery(householdId)).stream()
                .map(device -> new ScopedDevice(device.deviceId(), device.alias(), device.type().name(),
                        device.status().name(), device.roomKey()))
                .toList();
    }

    @Override
    public List<AssignedEdge> fetchEdgesOfHousehold(Long householdId) {
        return householdDeviceRegistryQueryService.handle(new GetEdgesOfHouseholdQuery(householdId)).stream()
                .map(edge -> new AssignedEdge(edge.edgeId(),
                        edge.edgeType() == EdgeType.MICROPHONE_EDGE ? "MICROPHONE" : "ACTUATOR"))
                .toList();
    }

    @Override
    public Optional<Long> fetchHouseholdOfDevice(Long deviceId) {
        return householdDeviceRegistryQueryService.handle(new GetHouseholdOfDeviceQuery(deviceId));
    }

    @Override
    public Long fetchEdgeCountByBusinessId(Long businessId) {
        return edgeQueryService.handle(new GetEdgeCountByBusinessIdQuery(businessId));
    }

    @Override
    public Long fetchDeviceCountByBusinessId(Long businessId) {
        return deviceQueryService.handle(
                new GetDeviceCountByBusinessIdQuery(businessId)
        );
    }
}
