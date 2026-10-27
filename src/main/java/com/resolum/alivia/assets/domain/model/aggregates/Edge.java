package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

@Entity
@Getter
public class Edge extends Asset {
    protected Edge() {
        super();
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_type", nullable = false)
    private EdgeType edgeType;

public Edge(Long businessId, String macAddress,String edgeType){
 super(businessId,macAddress);
    this.edgeType=EdgeType.valueOf(edgeType);
}

boolean isMicrophoneEdge(){

    return this.edgeType.equals(EdgeType.MICROPHONE_EDGE);
}

boolean isActuatorEdge() {

    return this.edgeType.equals(EdgeType.ACTUATOR_EDGE);
}
public Edge update(String macAddress,String edgeType){

   this.edgeType = EdgeType.valueOf(edgeType);
 this.updateMacAddress(macAddress);
 return this;

}

}
