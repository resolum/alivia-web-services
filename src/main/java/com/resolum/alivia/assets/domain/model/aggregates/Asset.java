package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
import com.resolum.alivia.assets.domain.model.events.AssetStatusChangedEvent;
import com.resolum.alivia.assets.domain.model.events.AssetUpdatedEvent;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetKind;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import jakarta.persistence.*;
import lombok.Getter;

@MappedSuperclass
@Getter
public abstract class Asset extends AuditableAbstractAggregateRoot<Asset> {
    protected  Asset() {

    }
    @Embedded
    private BusinessId businessId;

    @Embedded
    @AttributeOverride(name = "macAddress", column = @Column(name = "mac_address", nullable = false, length = 17))
    private MacAddress macAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_status", nullable = false, length = 16)
    private AssetStatus assetStatus;

    public Asset(Long businessId, String macAddress){
        this.businessId= new BusinessId(businessId);
        this.macAddress= new MacAddress(macAddress);
        this.assetStatus= AssetStatus.AVAILABLE;
    }

    public void updateMacAddress(String macAddress){
        this.macAddress= new MacAddress(macAddress);

    }

    public void updateAssetStatus(String assetStatus){
        if (assetStatus == null || assetStatus.isBlank()) {
            throw new IllegalArgumentException("Asset status cannot be null or blank.");
        }
        changeStatusTo(AssetStatus.valueOf(assetStatus.strip().toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Moves the asset to {@code target} or fails without mutating it when the transition is not allowed, and
     * announces the change so the edge services can synchronize.
     */
    public void changeStatusTo(AssetStatus target) {
        var previous = this.assetStatus;
        transitionTo(target);
        addDomainEvent(new AssetStatusChangedEvent(assetKind(), getId(), macAddress.macAddress(), assetTypeName(),
                previous, target));
    }

    /** The same transition without announcing it: assigning and unassigning announce their own event. */
    void transitionTo(AssetStatus target) {
        if (!this.assetStatus.canChangeTo(target)) {
            throw new InvalidAssetStatusTransition(this.assetStatus, target);
        }
        this.assetStatus = target;
    }

    /** Device or edge. */
    protected abstract AssetKind assetKind();

    /** Name of the device type or edge type. */
    protected abstract String assetTypeName();

    /** Announces that the MAC address or the type changed. */
    protected void announceUpdate() {
        addDomainEvent(new AssetUpdatedEvent(assetKind(), getId(), macAddress.macAddress(), assetTypeName(), assetStatus));
    }

    public boolean isAvailable() {
        return this.assetStatus.equals(AssetStatus.AVAILABLE);
    }

}
