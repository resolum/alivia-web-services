package com.resolum.alivia.assets.domain.model.commands;

public record RegisterEdgeCommand(Long businessId,String macAddress,String edgeType) {
}
