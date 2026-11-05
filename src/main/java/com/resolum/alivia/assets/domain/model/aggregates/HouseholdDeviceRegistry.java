package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent.Cause;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Everything Assets knows about the devices of one household: which devices are in it, in which room and with which
 * function, the edge services that run them, and the version of that table. There is one registry per household.
 *
 * <p>The business (Alivia) owns the devices. The registry applies the rules of the household's devices on its own
 * entries, with no queries: a device must be available, the plan limits how many of each type there can be, and
 * the plan includes one microphone per room. Who is allowed to change it is decided outside (the household,
 * the administrator of the business, or Operations for its technicians), and so is the work of installing.</p>
 *
 * <p>Every change of the table adds one to {@link #getTableVersion()} and announces the whole new table in a single
 * {@link HouseholdDevicesChangedEvent}. The registry has to be locked while it is changed, so versions never repeat
 * and each table is exactly the state of its version.</p>
 *
 * <p>Only the active entries are loaded; an entry that is closed stays in the table as history.</p>
 */
@Getter
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_household_device_registries_household", columnNames = "household_id"),
        @UniqueConstraint(name = "uk_household_device_registries_installation", columnNames = "installation_id")})
public class HouseholdDeviceRegistry extends AuditableAbstractAggregateRoot<HouseholdDeviceRegistry> {

    @Embedded
    @AttributeOverride(name = "businessId", column = @Column(name = "business_id", nullable = false))
    private BusinessId businessId;

    /** Household the registry belongs to (a Profiles id). Plain number: no foreign key crosses contexts. */
    @Column(name = "household_id", nullable = false)
    private Long householdId;

    /** Installation done for the household. */
    @Embedded
    @AttributeOverride(name = "installationId", column = @Column(name = "installation_id", nullable = false))
    private InstallationId installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RegistryStatus status;

    /** Grows by one with every change of the table; the edge services use it to tell which table is newer. */
    @Column(name = "table_version", nullable = false)
    private long tableVersion;

    /** Edge service that listens to the microphones of the household. */
    @OneToOne
    @JoinColumn(name = "microphone_edge")
    private Edge microphoneEdge;

    /** Edge service that runs the actuators of the household. */
    @OneToOne
    @JoinColumn(name = "actuator_edge")
    private Edge actuatorEdge;

    @OneToMany(mappedBy = "registry", cascade = CascadeType.ALL)
    @SQLRestriction("unassigned_at is null")
    private List<DeviceAssignment> entries = new ArrayList<>();

    protected HouseholdDeviceRegistry() {
    }

    private HouseholdDeviceRegistry(Long installationId, Long householdId, Long businessId) {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be a positive number.");
        }
        this.businessId = new BusinessId(businessId);
        this.installationId = new InstallationId(installationId);
        this.householdId = householdId;
        this.status = RegistryStatus.ACTIVE;
    }

    /** Opens the registry of a household when its installation is registered. It starts active and empty. */
    public static HouseholdDeviceRegistry open(Long installationId, Long householdId, Long businessId) {
        return new HouseholdDeviceRegistry(installationId, householdId, businessId);
    }

    public boolean isActive() {
        return status == RegistryStatus.ACTIVE;
    }

    /**
     * Puts an available edge in the place of its type (microphone or actuators) and leaves it {@code IN_USE}. The edge
     * that was there goes back to the inventory. Edges are not entries of the table of devices, so neither the version
     * nor the event change. Placing the edge that already holds its place changes nothing.
     *
     * @return the edge that left the installation, or empty when the place was free or the same edge was already in it
     * @throws InstallationNotActive when the registry is not active
     * @throws com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable when the edge is not {@code AVAILABLE}
     */
    public Optional<Edge> placeEdge(Edge edge) {
        if (!isActive()) {
            throw new InstallationNotActive(installationId.installationId());
        }
        var current = edge.getEdgeType() == EdgeType.MICROPHONE_EDGE ? microphoneEdge : actuatorEdge;
        if (current != null && current.getId().equals(edge.getId())) {
            return Optional.empty();
        }
        // placeInUse validates the status before it changes anything.
        edge.placeInUse();
        if (edge.getEdgeType() == EdgeType.MICROPHONE_EDGE) {
            microphoneEdge = edge;
        } else {
            actuatorEdge = edge;
        }
        if (current != null) {
            current.release();
        }
        return Optional.ofNullable(current);
    }

    /**
     * Takes an edge out of the installation; it goes back to the inventory (available, unless it was in maintenance or
     * broken). It is allowed even when the registry is no longer active, so a deactivated installation can be cleaned up.
     *
     * @throws EdgeNotAssigned when the edge is not one of the installation's edge services
     */
    public Edge removeEdge(Long edgeId) {
        Edge removed;
        if (microphoneEdge != null && microphoneEdge.getId().equals(edgeId)) {
            removed = microphoneEdge;
            microphoneEdge = null;
        } else if (actuatorEdge != null && actuatorEdge.getId().equals(edgeId)) {
            removed = actuatorEdge;
            actuatorEdge = null;
        } else {
            throw new EdgeNotAssigned(edgeId, installationId.installationId());
        }
        removed.release();
        return removed;
    }

    /**
     * Places an available device in a room of the household and leaves it {@code IN_USE}. Nothing changes if any rule
     * fails.
     *
     * @param contracted what the household's active subscription includes
     * @return the entry that records the placement
     */
    public DeviceAssignment register(Device device, String roomKey, String function, Long assignedBy,
                                     LocalDateTime at, ContractedActuators contracted) {
        if (!isActive()) {
            throw new InstallationNotActive(installationId.installationId());
        }
        if (roomKey == null || roomKey.isBlank()) {
            throw new IllegalArgumentException("Room cannot be null or blank.");
        }
        device.ensureAssignable();
        ensureCompatibleWithPlan(device.getDeviceType(), roomKey.strip(), contracted);

        // assignTo validates the rest of the data before it changes the device.
        var entry = device.assignTo(installationId.installationId(), householdId, roomKey, function, assignedBy, at);
        entry.attachTo(this);
        entries.add(entry);
        recordChange(Cause.DEVICE_ASSIGNED, device.getId());
        return entry;
    }

    /**
     * Takes a device out of the household. The entry is closed with who, when and why, and stays as history; the device
     * goes back to {@code AVAILABLE} when it was in use.
     *
     * @throws DeviceNotAssigned if the device is not in the registry
     */
    public DeviceAssignment remove(Long deviceId, String reason, Long actor, LocalDateTime at) {
        var entry = activeEntryOf(deviceId).orElseThrow(() -> new DeviceNotAssigned(deviceId));
        entry.unassign(reason, actor, at);
        recordChange(Cause.DEVICE_UNASSIGNED, deviceId);
        return entry;
    }

    /**
     * A device of the registry changed its status, its MAC address or its type, which the table shows. Does nothing for a
     * device that is not in the registry.
     */
    public void deviceChanged(Long deviceId) {
        if (activeEntryOf(deviceId).isPresent()) {
            recordChange(Cause.DEVICE_CHANGED, deviceId);
        }
    }

    public boolean hasActiveDevice(Long deviceId) {
        return activeEntryOf(deviceId).isPresent();
    }

    /** The complete table of devices of the household, with the current version. */
    public EdgeAccessTable table() {
        return EdgeAccessTable.of(installationId.installationId(), householdId, tableVersion, activeEntries().toList());
    }

    private Stream<DeviceAssignment> activeEntries() {
        return entries.stream().filter(DeviceAssignment::isActive);
    }

    private Optional<DeviceAssignment> activeEntryOf(Long deviceId) {
        return activeEntries().filter(entry -> entry.getDevice().getId().equals(deviceId)).findFirst();
    }

    /**
     * The household needs an active subscription (the caller checks it); an actuator fits when its type was contracted
     * and not all of that type are already placed; the microphone fits when its room has none yet.
     */
    private void ensureCompatibleWithPlan(DeviceType type, String roomKey, ContractedActuators contracted) {
        if (type == DeviceType.MICROPHONE) {
            var roomHasItsMicrophone = activeEntries().anyMatch(entry ->
                    entry.getDevice().getDeviceType() == DeviceType.MICROPHONE && entry.getRoomKey().equals(roomKey));
            if (roomHasItsMicrophone) {
                throw new DeviceIncompatibleWithPlan("The room already has its microphone.");
            }
            return;
        }
        var contractedQuantity = contracted.quantityOf(type);
        if (contractedQuantity == 0) {
            throw new DeviceIncompatibleWithPlan("The plan does not include " + type + " devices.");
        }
        var placed = activeEntries().filter(entry -> entry.getDevice().getDeviceType() == type).count();
        if (placed >= contractedQuantity) {
            throw new DeviceIncompatibleWithPlan(
                    "All " + contractedQuantity + " " + type + " devices of the plan are already assigned.");
        }
    }

    private void recordChange(Cause cause, Long deviceId) {
        tableVersion++;
        addDomainEvent(new HouseholdDevicesChangedEvent(cause, householdId, installationId.installationId(), deviceId, table()));
    }
}
