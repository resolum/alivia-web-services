package com.resolum.alivia.assets.domain.model.commands;

public record RegisterEdgeCommand(Long businessId,String macAddress,String edgeType) {

    public RegisterEdgeCommand {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be a positive number.");
        }
        if (macAddress == null || macAddress.isBlank()) {
            throw new IllegalArgumentException("Mac address cannot be null or blank.");
        }
        if (edgeType == null || edgeType.isBlank()) {
            throw new IllegalArgumentException("Edge type cannot be null or blank.");
        }
    }
}
