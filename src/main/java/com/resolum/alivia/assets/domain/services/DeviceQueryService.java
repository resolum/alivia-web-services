package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.queries.*;

import com.resolum.alivia.assets.domain.model.valueobjects.DeviceInventoryItem;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface DeviceQueryService {

    Optional<Device>handle(GetDeviceByIdQuery query);
    List<Device> handle(GetAllDevicesByBusinessIdQuery query);
    boolean handle(ExistsDeviceByMacAddressQuery query);
Optional<Device>handle(GetDeviceByMacAddressQuery query);
  Long handle(GetDeviceCountByBusinessIdQuery query);

    /** A page of the inventory of devices of the business, with the installation where each one is assigned. */
    Page<DeviceInventoryItem> handle(GetDevicesQuery query);

    /** One device of the inventory, with the installation where it is assigned; empty when it does not exist. */
    Optional<DeviceInventoryItem> handle(GetDeviceInventoryItemByIdQuery query);

}
