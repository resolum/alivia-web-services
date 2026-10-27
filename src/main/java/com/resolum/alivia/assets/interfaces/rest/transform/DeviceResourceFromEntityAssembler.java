package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.interfaces.rest.resources.DeviceResource;

public class DeviceResourceFromEntityAssembler {
    public static DeviceResource toResourceFromEntity(Device entity){

        return new DeviceResource( entity.getId(),entity.getBusinessId().businessId(),entity.getMacAddress().macAddress(),entity.getAssetStatus().name(),entity.getDeviceType().name());
    }
}
