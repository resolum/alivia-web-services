package com.resolum.alivia.assets.application.acl;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceNetworkCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceCountByBusinessIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCountByBusinessIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.domain.services.DeviceNetworkCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AssetsContextFacadeImpl implements AssetsContextFacade {
    private final DeviceQueryService deviceQueryService;
    private final DeviceCommandService deviceCommandService;
    private final EdgeCommandService edgeCommandService;
    private final EdgeQueryService edgeQueryService;
    private final DeviceNetworkCommandService deviceNetworkCommandService;
    private final DeviceAssignmentCommandService deviceAssignmentCommandService;

    public AssetsContextFacadeImpl(DeviceQueryService deviceQueryService, DeviceCommandService deviceCommandService, EdgeCommandService edgeCommandService, EdgeQueryService edgeQueryService,
                                   DeviceNetworkCommandService deviceNetworkCommandService,
                                   DeviceAssignmentCommandService deviceAssignmentCommandService) {
        this.deviceNetworkCommandService = deviceNetworkCommandService;
        this.deviceAssignmentCommandService = deviceAssignmentCommandService;
        this.deviceQueryService = deviceQueryService;
        this.deviceCommandService = deviceCommandService;
        this.edgeCommandService = edgeCommandService;
        this.edgeQueryService = edgeQueryService;
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
        return deviceNetworkCommandService.handle(new RegisterDeviceNetworkCommand(installationId, householdId, businessId));
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
