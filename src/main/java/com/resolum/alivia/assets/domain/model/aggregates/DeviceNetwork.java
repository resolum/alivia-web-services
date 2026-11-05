package com.resolum.alivia.assets.domain.model.aggregates;

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
 * <p>The work of installing and maintaining (who does it, when it is confirmed) belongs to Operations; Assets only
 * keeps which devices are in the household. A household has at most one active network.</p>
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

    /** Registers the network of an installation; it starts {@code ACTIVE} without edges. */
    public DeviceNetwork(Long installationId, Long householdId, Long businessId) {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be a positive number.");
        }
        this.businessId = new BusinessId(businessId);
        this.installationId = new InstallationId(installationId);
        this.householdId = householdId;
        this.networkStatus = NetworkStatus.ACTIVE;
    }

    public boolean isActive() {
        return networkStatus == NetworkStatus.ACTIVE;
    }
}
