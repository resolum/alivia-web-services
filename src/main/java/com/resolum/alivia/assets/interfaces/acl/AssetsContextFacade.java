package com.resolum.alivia.assets.interfaces.acl;

import java.util.Optional;

public interface AssetsContextFacade {

    /** Id of the device with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchDeviceByMacAddress(String macAddress);

    /** Id of the edge with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchEdgeByMacAddress(String macAddress);

    /**
     * Registers the network of an installation done for a household, so devices can be placed in it.
     * Fails when the business does not exist or the installation or the household's active network is already
     * registered.
     *
     * @return the id of the network
     */
    Long registerInstallation(Long installationId, Long householdId, Long businessId);

    Long fetchEdgeCountByBusinessId(Long businessId);
    Long fetchDeviceCountByBusinessId(Long businessId);
}
