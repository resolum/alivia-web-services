package com.resolum.alivia.assets.interfaces.acl;

import java.util.Optional;

public interface AssetsContextFacade {

    /** Id of the device with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchDeviceByMacAddress(String macAddress);

    /** Id of the edge with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchEdgeByMacAddress(String macAddress);

    Long fetchEdgeCountByBusinessId(Long businessId);
    Long fetchDeviceCountByBusinessId(Long businessId);
}
