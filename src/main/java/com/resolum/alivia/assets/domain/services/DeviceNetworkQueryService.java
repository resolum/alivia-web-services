package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;

import java.util.Optional;

public interface DeviceNetworkQueryService {

    Optional<DeviceNetwork> handle(GetDeviceNetworkByIdQuery query);
    Optional<DeviceNetwork> handle(GetDeviceNetworkByInstallationIdQuery query);

    /** The table of devices the household keeps for its edge services; empty when the installation is unknown. */
    Optional<EdgeAccessTable> handle(GetEdgeAccessTableByInstallationIdQuery query);
}
