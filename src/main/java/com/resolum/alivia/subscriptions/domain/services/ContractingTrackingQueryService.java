package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.queries.GetContractingTrackingQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetMyContractingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.MyContracting;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;

import java.util.Optional;

public interface ContractingTrackingQueryService {
    ContractingTracking handle(GetContractingTrackingQuery query);

    /** The stage of the account's latest request; empty when the account has none. */
    Optional<MyContracting> handle(GetMyContractingQuery query);
}
