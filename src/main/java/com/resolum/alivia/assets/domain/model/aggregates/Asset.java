package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
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

    /**
     * Counts the changes of the asset. Two requests that change the same asset from the same state cannot both be
     * stored: the later one fails instead of overwriting the first (a status change, for example, would erase an
     * assignment made at the same time).
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

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

    /** Moves the asset to {@code target} or fails without mutating it when the transition is not allowed. */
    public void changeStatusTo(AssetStatus target) {
        if (!this.assetStatus.canChangeTo(target)) {
            throw new InvalidAssetStatusTransition(this.assetStatus, target);
        }
        this.assetStatus = target;
    }

    public boolean isAvailable() {
        return this.assetStatus.equals(AssetStatus.AVAILABLE);
    }

}
