package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.queries.ExistsDeviceByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetAllDevicesByBusinessIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDevIceByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByMacAddressQuery;

import java.util.List;
import java.util.Optional;

public interface DeviceQueryService {

    Optional<Device>handle(GetDevIceByIdQuery query);
    List<Device> handle(GetAllDevicesByBusinessIdQuery query);
    boolean handle(ExistsDeviceByMacAddressQuery query);
Optional<Device>handle(GetDeviceByMacAddressQuery query);

}
