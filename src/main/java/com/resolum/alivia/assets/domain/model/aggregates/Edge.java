package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetKind;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_edges_mac_address", columnNames = "mac_address"))
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
    this.edgeType=parseType(edgeType);
}

@Override
protected AssetKind assetKind() {
    return AssetKind.EDGE;
}

@Override
protected String assetTypeName() {
    return edgeType.name();
}

boolean isMicrophoneEdge(){

    return this.edgeType.equals(EdgeType.MICROPHONE_EDGE);
}

boolean isActuatorEdge() {

    return this.edgeType.equals(EdgeType.ACTUATOR_EDGE);
}
public Edge update(String macAddress,String edgeType){

   var newType = parseType(edgeType);
   var previousMacAddress = getMacAddress();
   var previousType = this.edgeType;
   this.updateMacAddress(macAddress);
   this.edgeType = newType;
   if (!getMacAddress().equals(previousMacAddress) || newType != previousType) {
       announceUpdate();
   }
   return this;

}

    private static EdgeType parseType(String edgeType) {
        if (edgeType == null || edgeType.isBlank()) {
            throw new IllegalArgumentException("Edge type cannot be null or blank.");
        }
        try {
            return EdgeType.valueOf(edgeType.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown edge type: " + edgeType.strip() + ".");
        }
    }

}
