package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByDeviceIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByInstallationIdQuery;

import java.util.Optional;

public interface DeviceNetworkQueryService {

    Optional<DeviceNetwork> handle(GetDeviceNetworkByIdQuery query);
    Optional<DeviceNetwork> handle(GetDeviceNetworkByInstallationIdQuery query);
    Optional<DeviceNetwork> handle(GetDeviceNetworkByEdgeIdQuery query);
    Optional<DeviceNetwork> handle(GetDeviceNetworkByDeviceIdQuery query);
}
