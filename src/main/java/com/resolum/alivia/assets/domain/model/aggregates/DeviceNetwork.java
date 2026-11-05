package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.List;

/**
 * The IoT devices of one installation in a household. The business (Alivia) owns the devices; the
 * household is the one the installation was done for, and it is the household that decides who may
 * place devices in it. A household has at most one {@code ACTIVE} network.
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
   @OneToOne
   @JoinColumn(name = "microphone_edge")
    private Edge microphoneEdge;
    @OneToOne
    @JoinColumn(name = "actuator_id")
    private Edge actuatorEdge;
    @OneToOne
    private Device microphone;

    @ManyToMany
    @JoinTable(
            name = "network_actuators",
            joinColumns = @JoinColumn(name = "network_id"),
            inverseJoinColumns = @JoinColumn(name = "device_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_network_actuators_device_id",
                    columnNames = "device_id"
            )
    )
    private List<Device> actuators;

    public DeviceNetwork(){}

    /** Registers the network of an installation; it starts {@code ACTIVE} without edges or devices. */
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

   void addActuatorEdge(Edge actuatorEdge){

    if (this.actuatorEdge ==null && networkStatus== NetworkStatus.ACTIVE){
     this.actuatorEdge= actuatorEdge;
    }

    else {
        throw new IllegalStateException("Actuator edge already exists or network is not active.");
    }
   }
   void addMicrophoneEdge(Edge microphoneEdge){

        if (this.microphoneEdge ==null && networkStatus== NetworkStatus.ACTIVE){
            this.microphoneEdge= microphoneEdge;
        }
        else {
            throw new IllegalStateException("Microphone edge already exists or network is not active.");
        }
   }


    void removeEdge(Long edgeId) {
        if (edgeId<=0){
            throw new IllegalArgumentException("Edge ID must be a positive number.");
        }
    }

   void removeDevice(Long deviceId){

      if (deviceId<=0){
          throw new IllegalArgumentException("Device ID must be a positive number.");
      }
   }


}
