package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
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

boolean isMicrophoneEdge(){

    return this.edgeType.equals(EdgeType.MICROPHONE_EDGE);
}

boolean isActuatorEdge() {

    return this.edgeType.equals(EdgeType.ACTUATOR_EDGE);
}

    /** Only an {@code AVAILABLE} edge can be placed in an installation; any other status is a conflict. */
    void ensureAssignable() {
        if (!isAvailable()) {
            throw new EdgeNotAvailable(getId(), getAssetStatus());
        }
    }

    /** Leaves the edge {@code IN_USE} because it was placed in an installation. Untouched when it is not available. */
    void placeInUse() {
        ensureAssignable();
        changeStatusTo(AssetStatus.IN_USE);
    }

    /** The edge left its installation: it is available again, unless it was in maintenance or broken. */
    void release() {
        if (getAssetStatus() == AssetStatus.IN_USE) {
            changeStatusTo(AssetStatus.AVAILABLE);
        }
    }

    /** Whether {@link #update} with this type would change the type of the edge (it also validates the type). */
    public boolean changesTypeTo(String edgeType) {
        return parseType(edgeType) != this.edgeType;
    }
public Edge update(String macAddress,String edgeType){

   var newType = parseType(edgeType);
   this.updateMacAddress(macAddress);
   this.edgeType = newType;
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
