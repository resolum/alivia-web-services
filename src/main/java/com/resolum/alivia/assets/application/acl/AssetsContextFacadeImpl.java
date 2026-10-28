package com.resolum.alivia.assets.application.acl;

import com.resolum.alivia.assets.domain.model.queries.GetDeviceByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceCountByBusinessIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCountByBusinessIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import org.springframework.stereotype.Service;

@Service
public class AssetsContextFacadeImpl implements AssetsContextFacade {
    private final DeviceQueryService deviceQueryService;
    private final DeviceCommandService deviceCommandService;
    private final EdgeCommandService edgeCommandService;
    private final EdgeQueryService edgeQueryService;

    public AssetsContextFacadeImpl(DeviceQueryService deviceQueryService, DeviceCommandService deviceCommandService, EdgeCommandService edgeCommandService, EdgeQueryService edgeQueryService) {
        this.deviceQueryService = deviceQueryService;
        this.deviceCommandService = deviceCommandService;
        this.edgeCommandService = edgeCommandService;
        this.edgeQueryService = edgeQueryService;
    }


    @Override
    public Long fetchDeviceByMacAddress(String macAddress) {
      var device=  deviceQueryService.handle(new GetDeviceByMacAddressQuery(macAddress));
        return device.isEmpty()?Long.valueOf(0L):device.get().getId();
    }

    @Override
    public Long fetchEdgeByMacAddress(String macAddress) {

        var edge= edgeQueryService.handle(new GetEdgeByMacAddressQuery(macAddress));
        return edge.isEmpty()?Long.valueOf(0L):edge.get().getId();

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
