package com.resolum.alivia.assets.interfaces.acl;

public interface AssetsContextFacade {

    Long  fetchDeviceByMacAddress(String macAddress);
    Long fetchEdgeByMacAddress(String macAddress);
    Long fetchEdgeCountByBusinessId(Long businessId);
    Long fetchDeviceCountByBusinessId(Long businessId);
}
