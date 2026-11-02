package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import java.time.LocalDateTime;
import java.util.Map;

public record ContractVersionResource(int version, ContractAction action, Long actorUserId,
                                      Map<String, String> snapshot, LocalDateTime createdAt) {
}
