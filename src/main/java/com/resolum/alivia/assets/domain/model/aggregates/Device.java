package com.resolum.alivia.assets.domain.model.aggregates;

import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

@Entity
@Getter
public class Device extends Asset {

    protected Device() {
        super();
    }

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "device_type" ,nullable = false)

private DeviceType deviceType;

public Device update(String macAddress ,String deviceType){

    updateMacAddress(macAddress);
    this.deviceType= DeviceType.valueOf(deviceType);
    return this;
}
    public Device(String macAddress,String deviceType,Long businessId){
    super(businessId,macAddress);
    this.deviceType= DeviceType.valueOf(deviceType);
    }
}
