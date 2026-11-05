package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Placement of a device in a household: the room it serves and the function it has there. It is the
 * record that ties the business inventory to the household; it stays {@linkplain #isActive() active}
 * while {@code unassignedAt} is empty, and a device has at most one active assignment.
 *
 * <p>It is also the history of the device: {@linkplain #unassign unassigning} closes the record with who
 * did it, when and why, and never deletes it. Placing the device again creates a new record.</p>
 */
@Entity
@Getter
public class DeviceAssignment extends AuditableAbstractAggregateRoot<DeviceAssignment> {

    public static final int ROOM_KEY_MAX_LENGTH = 60;
    public static final int FUNCTION_MAX_LENGTH = 80;
    public static final int UNASSIGN_REASON_MAX_LENGTH = 300;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_device_assignments_device"))
    private Device device;

    /**
     * Installation (a {@link DeviceNetwork}) the device was placed in. Nullable in the table only because it was
     * added after the table existed; every new assignment requires it.
     */
    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    /** Key of the room in the contracted configuration (the rooms have no aggregate of their own yet). */
    @Column(name = "room_key", nullable = false, length = ROOM_KEY_MAX_LENGTH)
    private String roomKey;

    @Column(name = "device_function", nullable = false, length = FUNCTION_MAX_LENGTH)
    private String function;

    /** Account that made the assignment. */
    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    /** Account that closed the assignment; set together with {@code unassignedAt}. */
    @Column(name = "unassigned_by")
    private Long unassignedBy;

    /** Why the device was taken out of the household; set together with {@code unassignedAt}. */
    @Column(name = "unassign_reason", length = UNASSIGN_REASON_MAX_LENGTH)
    private String unassignReason;

    protected DeviceAssignment() {
    }

    DeviceAssignment(Device device, Long installationId, Long householdId, String roomKey, String function,
                     Long assignedBy, LocalDateTime assignedAt) {
        if (device == null) {
            throw new IllegalArgumentException("The device is required.");
        }
        if (installationId == null || installationId <= 0) {
            throw new IllegalArgumentException("Installation ID must be a positive number.");
        }
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be a positive number.");
        }
        if (assignedBy == null || assignedBy <= 0) {
            throw new IllegalArgumentException("The account that assigns the device is required.");
        }
        if (assignedAt == null) {
            throw new IllegalArgumentException("The assignment date is required.");
        }
        this.device = device;
        this.installationId = installationId;
        this.householdId = householdId;
        this.roomKey = requireText(roomKey, "Room", ROOM_KEY_MAX_LENGTH);
        this.function = requireText(function, "Function", FUNCTION_MAX_LENGTH);
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public boolean isActive() {
        return unassignedAt == null;
    }

    /**
     * Closes the assignment: the record stays as history with who closed it, when and why, and the device
     * goes back to {@code AVAILABLE} when it was {@code IN_USE}. A device that meanwhile went to maintenance or
     * broke keeps that status: leaving the household does not repair it.
     *
     * <p>Everything is validated before anything changes, so a failure leaves the assignment and the device
     * as they were.</p>
     *
     * @throws DeviceNotAssigned if the assignment was already closed
     */
    public void unassign(String reason, Long actor, LocalDateTime at) {
        var cleanReason = requireText(reason, "Reason", UNASSIGN_REASON_MAX_LENGTH);
        if (actor == null || actor <= 0) {
            throw new IllegalArgumentException("The account that unassigns the device is required.");
        }
        if (at == null) {
            throw new IllegalArgumentException("The unassignment date is required.");
        }
        if (!isActive()) {
            throw new DeviceNotAssigned(device.getId());
        }
        if (at.isBefore(assignedAt)) {
            throw new IllegalArgumentException("The unassignment cannot be before the assignment.");
        }
        if (device.getAssetStatus() == AssetStatus.IN_USE) {
            device.changeStatusTo(AssetStatus.AVAILABLE);
        }
        this.unassignedAt = at;
        this.unassignedBy = actor;
        this.unassignReason = cleanReason;
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null or blank.");
        }
        var trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(name + " cannot exceed " + maxLength + " characters.");
        }
        return trimmed;
    }
}
