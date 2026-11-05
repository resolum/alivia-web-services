package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.model.events.AssetUpdatedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceAssignedEvent;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * IoT device identified by its physical (MAC) address, which is unique among devices.
 * A new device is registered as {@code AVAILABLE}.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_devices_mac_address", columnNames = "mac_address"))
@Getter
public class Device extends Asset {

    public static final int ALIAS_MAX_LENGTH = 80;

    protected Device() {
        super();
    }

    @Column(name = "alias", nullable = false, length = ALIAS_MAX_LENGTH)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private DeviceType deviceType;

    public Device(String macAddress, String alias, String deviceType, Long businessId) {
        super(businessId, macAddress);
        this.alias = requireAlias(alias);
        this.deviceType = parseType(deviceType);
    }

    public Device update(String macAddress, String deviceType) {
        var newType = parseType(deviceType);
        var previousMacAddress = getMacAddress();
        var previousType = this.deviceType;
        updateMacAddress(macAddress);
        this.deviceType = newType;
        if (!getMacAddress().equals(previousMacAddress) || newType != previousType) {
            addDomainEvent(new AssetUpdatedEvent(this));
        }
        return this;
    }

    /** Only an {@code AVAILABLE} device can be assigned; any other status is a conflict. */
    public void ensureAssignable() {
        if (!isAvailable()) {
            throw new DeviceNotAvailable(getId(), getAssetStatus());
        }
    }

    /**
     * Places the device in a room of the household of an installation, with a function, and leaves it
     * {@code IN_USE}. The assignment is
     * built and validated before the status changes, so the device is untouched when anything is invalid.
     */
    public DeviceAssignment assignTo(Long installationId, Long householdId, String roomKey, String function,
                                     Long assignedBy, LocalDateTime assignedAt) {
        ensureAssignable();
        var assignment = new DeviceAssignment(this, installationId, householdId, roomKey, function, assignedBy,
                assignedAt);
        transitionTo(AssetStatus.IN_USE);
        assignment.addDomainEvent(new DeviceAssignedEvent(assignment));
        return assignment;
    }

    /** Whether {@link #update} with this type would change the type of the device (it also validates the type). */
    public boolean changesTypeTo(String deviceType) {
        return parseType(deviceType) != this.deviceType;
    }

    private static String requireAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Device alias cannot be null or blank.");
        }
        var trimmed = alias.strip();
        if (trimmed.length() > ALIAS_MAX_LENGTH) {
            throw new IllegalArgumentException("Device alias cannot exceed " + ALIAS_MAX_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static DeviceType parseType(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            throw new IllegalArgumentException("Device type cannot be null or blank.");
        }
        try {
            return DeviceType.valueOf(deviceType.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown device type: " + deviceType.strip() + ".");
        }
    }
}
