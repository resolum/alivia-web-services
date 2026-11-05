package com.resolum.alivia.assets.domain.model.valueobjects;

import java.util.Map;

/** Actuators a household contracted, by device type. A type that was not contracted counts as zero. */
public record ContractedActuators(Map<DeviceType, Integer> quantityByType) {

    public ContractedActuators {
        quantityByType = Map.copyOf(quantityByType);
    }

    public int quantityOf(DeviceType type) {
        return quantityByType.getOrDefault(type, 0);
    }
}
