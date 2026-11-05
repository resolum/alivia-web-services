package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.*;
import lombok.Getter;

/**
 * The network of one installation in a household. The business (Alivia) owns the devices; the household is the one
 * the installation was done for. The devices of the network are the ones with an active assignment to its
 * installation (every {@code DeviceType}), and the edge services that run them are the two edges below.
 *
 * <p>It starts {@code PENDING_CONFIRMATION}, while the technicians of the business place the devices, and becomes
 * {@code ACTIVE} when they confirm the installation. A household has at most one network that is pending or active.</p>
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_device_networks_installation", columnNames = "installation_id"))
public class DeviceNetwork extends AuditableAbstractAggregateRoot<DeviceNetwork> {
    @Embedded
    private BusinessId businessId;
    @Embedded
    private InstallationId installationId;
    /** Household of the installation (a Profiles id). Plain number: no foreign key crosses contexts. */
    @Column(name = "household_id")
    private Long householdId;
    @Enumerated(EnumType.STRING)
    private NetworkStatus networkStatus;
    /** Edge service that listens to the microphones of the household. */
    @OneToOne
    @JoinColumn(name = "microphone_edge")
    private Edge microphoneEdge;
    /** Edge service that runs the actuators of the household. */
    @OneToOne
    @JoinColumn(name = "actuator_id")
    private Edge actuatorEdge;

    public DeviceNetwork(){}

    /** Registers the network of an installation; it starts {@code PENDING_CONFIRMATION} without edges. */
    public DeviceNetwork(Long installationId, Long householdId, Long businessId) {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be a positive number.");
        }
        this.businessId = new BusinessId(businessId);
        this.installationId = new InstallationId(installationId);
        this.householdId = householdId;
        this.networkStatus = NetworkStatus.PENDING_CONFIRMATION;
    }

    public boolean isPendingConfirmation() {
        return networkStatus == NetworkStatus.PENDING_CONFIRMATION;
    }

    public boolean isActive() {
        return networkStatus == NetworkStatus.ACTIVE;
    }

    /** Devices can be placed in the network while it is pending confirmation or active. */
    public boolean isOpen() {
        return isPendingConfirmation() || isActive();
    }

    /** The technicians confirmed the installation. Confirming it again changes nothing. */
    public void confirm() {
        if (isActive()) {
            return;
        }
        if (!isPendingConfirmation()) {
            throw new InstallationNotActive(installationId.installationId());
        }
        this.networkStatus = NetworkStatus.ACTIVE;
    }
}
