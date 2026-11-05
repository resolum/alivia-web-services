package com.resolum.alivia.assets.domain.model.queries;

public record ExistsEdgeByMacAddressQuery(String macAddress) {
    public ExistsEdgeByMacAddressQuery{

        if (macAddress==null||macAddress.isBlank()){
            throw new IllegalArgumentException("mac address cannot be null or blank");
        }
    }
}
