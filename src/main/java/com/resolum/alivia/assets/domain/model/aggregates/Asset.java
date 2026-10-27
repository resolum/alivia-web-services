package com.resolum.alivia.assets.domain.model.aggregates;

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
private     BusinessId businessId;
  @Embedded
    private MacAddress macAddress;
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "asset_status", nullable = false)
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
      this.assetStatus= AssetStatus.valueOf(assetStatus);
  }

 public  boolean isAvailable() {
      return this.assetStatus.equals(AssetStatus.AVAILABLE);
  }

}
