package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface DeviceQueryService {

    Optional<Device>handle(GetDevIceByIdQuery query);
    List<Device> handle(GetAllDevicesByBusinessIdQuery query);
    boolean handle(ExistsDeviceByMacAddressQuery query);
Optional<Device>handle(GetDeviceByMacAddressQuery query);
  Long handle(GetDeviceCountByBusinessIdQuery query);

}
