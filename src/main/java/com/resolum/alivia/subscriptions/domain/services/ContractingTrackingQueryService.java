package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.queries.GetContractingTrackingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;

public interface ContractingTrackingQueryService {
    ContractingTracking handle(GetContractingTrackingQuery query);
}
