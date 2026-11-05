package com.resolum.alivia.assets.interfaces.acl;

import java.util.Optional;

public interface AssetsContextFacade {

    /** Id of the device with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchDeviceByMacAddress(String macAddress);

    /** Id of the edge with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchEdgeByMacAddress(String macAddress);

    /**
     * Registers the network of an installation that the technicians of the business start in a household, so they
     * can place devices in it. It stays pending until {@link #confirmInstallation}. Fails when the business does not
     * exist or the installation, or an open network of the household, is already registered.
     *
     * @return the id of the network
     */
    Long registerInstallation(Long installationId, Long householdId, Long businessId);

    /**
     * The technicians confirmed the installation: its network becomes active and the household takes over.
     * Confirming twice changes nothing; an unknown installation is an error.
     */
    void confirmInstallation(Long installationId);

    Long fetchEdgeCountByBusinessId(Long businessId);
    Long fetchDeviceCountByBusinessId(Long businessId);
}
