package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.List;

@Getter
@Entity
public class DeviceNetwork extends AuditableAbstractAggregateRoot<DeviceNetwork> {
    @Embedded
    @JoinColumn(name = "business_id")
    private BusinessId businessId;
    @Embedded
    @JoinColumn(name = "installation_id")
    private InstallationId installationId;
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
   public  DeviceNetwork(Long installationId,Long businessId,Edge microphoneEdge, Edge actuatorEdge,Device microphone, List<Device> actuators){
       this.businessId= new BusinessId(businessId);
       this.installationId= new InstallationId(installationId);
       this.actuatorEdge= actuatorEdge;
       this.microphoneEdge= microphoneEdge;
       this.microphone= microphone;
       this.actuators=actuators;
   }

   void addActuatorEdge(Edge actuatorEdge){

    if (this.actuatorEdge ==null){

    }

   }
   void addMicrophoneEdge(Edge microphoneEdge){

   }


    void removeEdge(Long edgeId) {}

   void removeDevice(Long deviceId){}


}
