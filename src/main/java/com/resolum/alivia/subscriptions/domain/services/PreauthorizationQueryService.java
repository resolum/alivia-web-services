package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PreauthorizationResult;

public interface PreauthorizationQueryService {
    PreauthorizationResult get(String authenticatedUserId, Long subscriptionId);
}
