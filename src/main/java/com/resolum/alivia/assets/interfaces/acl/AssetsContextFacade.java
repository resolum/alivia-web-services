package com.resolum.alivia.assets.interfaces.acl;

import java.util.Optional;

public interface AssetsContextFacade {

    /** Id of the device with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchDeviceByMacAddress(String macAddress);

    /** Id of the edge with that MAC address (any spelling), or empty when none is registered. */
    Optional<Long> fetchEdgeByMacAddress(String macAddress);

    /**
     * Registers the installation done for a household, which opens the registry of the household's devices so they can
     * be placed in it. Fails when the business does not exist or the installation, or an installation of that household,
     * is already registered.
     *
     * @return the id of the registry
     */
    Long registerInstallation(Long installationId, Long householdId, Long businessId);

    /**
     * Places an available device in a room of the household of an installation, on behalf of another context that
     * already decided that the acting account may do it (Operations, for the work of its technicians). Assets does
     * not ask who the account is: it applies the inventory rules (device available, compatible with the plan, one
     * active assignment per device).
     *
     * @return the id of the assignment
     */
    Long assignDevice(Long installationId, Long deviceId, String roomKey, String function, Long actingUserId);

    /**
     * Takes a device out of its household on behalf of another context that already authorized the acting
     * account. The assignment is closed with the reason, never deleted.
     */
    void unassignDevice(Long deviceId, String reason, Long actingUserId);

    Long fetchEdgeCountByBusinessId(Long businessId);
    Long fetchDeviceCountByBusinessId(Long businessId);
}
