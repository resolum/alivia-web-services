package com.resolum.alivia.assets.domain.model.queries;

public record ExistsDeviceByMacAddressQuery(String macAddress) {
    public ExistsDeviceByMacAddressQuery{
        if (macAddress==null|| macAddress.isBlank()){
            throw new IllegalArgumentException("Mac address cannot be null or blank");
        }
    }
}
