package com.resolum.alivia.assets.interfaces.acl;

import java.util.List;
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

    /**
     * Where an edge service is assigned, as primitives: the installation, the household and the devices placed there.
     * Empty when the edge is not assigned to an active installation. It lets another context (Tracking) tell which home a
     * report of the edge is about, and that a device it names belongs to that home.
     */
    Optional<EdgeScope> fetchEdgeScope(Long edgeId);

    /**
     * @param edgeId         the edge
     * @param edgeKind       {@code MICROPHONE} or {@code ACTUATOR}
     * @param installationId the installation where it is assigned
     * @param householdId    the household of the installation
     * @param devices        the devices placed in the installation
     */
    record EdgeScope(Long edgeId, String edgeKind, Long installationId, Long householdId, List<ScopedDevice> devices) {
    }

    /**
     * @param deviceId the device
     * @param alias    the name the business gave it
     * @param type     {@code MICROPHONE}, {@code DOOR}, {@code WINDOW}, {@code CURTAIN} or {@code LIGHT}
     * @param status   its life-cycle status, for example {@code IN_USE}
     * @param roomKey  the room where it is placed
     */
    record ScopedDevice(Long deviceId, String alias, String type, String status, String roomKey) {
    }

    Long fetchEdgeCountByBusinessId(Long businessId);
    Long fetchDeviceCountByBusinessId(Long businessId);
}
